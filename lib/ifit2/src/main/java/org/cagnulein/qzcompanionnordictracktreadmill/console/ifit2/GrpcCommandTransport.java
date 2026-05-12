package org.cagnulein.qzcompanionnordictracktreadmill.console.ifit2;

import android.content.Context;

import com.ifit.glassos.workout.InclineRequest;
import com.ifit.glassos.workout.InclineServiceGrpc;
import com.ifit.glassos.workout.ResistanceRequest;
import com.ifit.glassos.workout.ResistanceServiceGrpc;
import com.ifit.glassos.workout.SpeedRequest;
import com.ifit.glassos.workout.SpeedServiceGrpc;
import com.ifit.glassos.workout.WorkoutResult;

import org.cagnulein.qzcompanionnordictracktreadmill.device.Device;
import org.cagnulein.qzcompanionnordictracktreadmill.device.DeviceLogTags;
import org.cagnulein.qzcompanionnordictracktreadmill.command.Command;
import org.cagnulein.qzcompanionnordictracktreadmill.command.InclineCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.command.ResistanceCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.command.SpeedCommand;

import java.util.concurrent.TimeUnit;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.okhttp.OkHttpChannelBuilder;

public final class GrpcCommandTransport implements CommandTransport {
    private static final String LOG_TAG = DeviceLogTags.DISPATCH;
    private static final Metadata.Key<String> CLIENT_ID_HEADER =
            Metadata.Key.of("client_id", Metadata.ASCII_STRING_MARSHALLER);

    private final Context context;
    private ManagedChannel channel;
    private InclineServiceGrpc.InclineServiceBlockingStub incline;
    private ResistanceServiceGrpc.ResistanceServiceBlockingStub resistance;
    private SpeedServiceGrpc.SpeedServiceBlockingStub speed;
    private boolean disabled;

    public GrpcCommandTransport(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean apply(Command command, Device.Logger logger) {
        if (!(command instanceof InclineCommand) && !(command instanceof ResistanceCommand) && !(command instanceof SpeedCommand)) {
            return false;
        }
        if (disabled) return false;

        try {
            ensureChannel();
            WorkoutResult result;
            if (command instanceof InclineCommand) {
                result = incline.withDeadlineAfter(2, TimeUnit.SECONDS)
                        .setIncline(InclineRequest.newBuilder().setPercent(command.value).build());
            } else if (command instanceof ResistanceCommand) {
                result = resistance.withDeadlineAfter(2, TimeUnit.SECONDS)
                        .setResistance(ResistanceRequest.newBuilder().setResistance(command.value).build());
            } else {
                result = speed.withDeadlineAfter(2, TimeUnit.SECONDS)
                        .setSpeed(SpeedRequest.newBuilder().setKph(command.value).build());
            }

            if (result.hasSuccess() && result.getSuccess()) {
                logger.log(Device.Logger.DEBUG, LOG_TAG, "glassos applied: " + command);
                return true;
            }
            logger.log(Device.Logger.WARN, LOG_TAG, "glassos rejected: " + command);
            return false;
        } catch (Exception e) {
            logger.log(Device.Logger.WARN, LOG_TAG, "glassos channel error: " + e.getMessage());
            shutdown();
            return false;
        }
    }

    public synchronized void shutdown() {
        if (channel != null) {
            channel.shutdownNow();
            channel = null;
            incline = null;
            resistance = null;
            speed = null;
        }
    }

    private synchronized void ensureChannel() throws Exception {
        if (channel != null) return;

        GrpcCredentials credentials = GrpcCredentials.load(context);
        channel = OkHttpChannelBuilder
                .forAddress("localhost", 54321)
                .overrideAuthority("localhost:54321")
                .sslSocketFactory(credentials.sslContext().getSocketFactory())
                .hostnameVerifier((hostname, session) -> true)
                .intercept(clientIdInterceptor())
                .build();
        incline = InclineServiceGrpc.newBlockingStub(channel);
        resistance = ResistanceServiceGrpc.newBlockingStub(channel);
        speed = SpeedServiceGrpc.newBlockingStub(channel);
    }

    private static ClientInterceptor clientIdInterceptor() {
        return new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                    MethodDescriptor<ReqT, RespT> method,
                    CallOptions callOptions,
                    Channel next) {
                return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                        next.newCall(method, callOptions)) {
                    @Override
                    public void start(Listener<RespT> responseListener, Metadata headers) {
                        headers.put(CLIENT_ID_HEADER, GrpcCredentials.CLIENT_ID_HEADER_VALUE);
                        super.start(responseListener, headers);
                    }
                };
            }
        };
    }
}
