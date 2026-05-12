package org.cagnulein.qzcompanionnordictracktreadmill.console.ifit2;

import android.content.Context;

import com.ifit.glassos.util.Empty;
import com.ifit.glassos.workout.HeartRateServiceGrpc;
import com.ifit.glassos.workout.InclineServiceGrpc;
import com.ifit.glassos.workout.ResistanceServiceGrpc;
import com.ifit.glassos.workout.RpmServiceGrpc;
import com.ifit.glassos.workout.SpeedServiceGrpc;
import com.ifit.glassos.workout.WattsServiceGrpc;
import com.ifit.glassos.workout.WorkoutServiceGrpc;
import com.ifit.glassos.workout.WorkoutState;
import com.ifit.glassos.workout.WorkoutStateMessage;

import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.CadenceTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.HeartRateTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.InclineTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.ResistanceTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.SpeedTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.Telemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.TelemetryReader;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.WattsTelemetry;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.stub.StreamObserver;
import io.grpc.okhttp.OkHttpChannelBuilder;

public final class GrpcTelemetryReader implements TelemetryReader {
    private static final Metadata.Key<String> CLIENT_ID_HEADER =
            Metadata.Key.of("client_id", Metadata.ASCII_STRING_MARSHALLER);

    public static volatile Consumer<Exception> onError = e -> {};
    public static volatile Consumer<String> onLine = s -> {};

    private final Context context;
    private volatile Consumer<Telemetry> listener;
    private ManagedChannel channel;
    private boolean started;
    private final AtomicBoolean workoutActive = new AtomicBoolean(false);

    public GrpcTelemetryReader(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public synchronized void read() throws IOException {
        if (started) return;
        try {
            GrpcCredentials credentials = GrpcCredentials.load(context);
            channel = OkHttpChannelBuilder
                    .forAddress("localhost", 54321)
                    .overrideAuthority("localhost:54321")
                    .sslSocketFactory(credentials.sslContext().getSocketFactory())
                    .hostnameVerifier((hostname, session) -> true)
                    .intercept(clientIdInterceptor())
                    .build();

            Empty empty = Empty.newBuilder().build();

            safePoll(() -> {
                WorkoutStateMessage event = WorkoutServiceGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(2, TimeUnit.SECONDS)
                        .getWorkoutState(Empty.newBuilder().build());
                if (event.getWorkoutState() == WorkoutState.WORKOUT_STATE_RUNNING) activateMetrics();
            });

            WorkoutServiceGrpc.WorkoutServiceStub workoutAsync = WorkoutServiceGrpc.newStub(channel);
            workoutAsync.workoutStateChanged(empty, new StreamObserver<WorkoutStateMessage>() {
                @Override
                public void onNext(WorkoutStateMessage event) {
                    if (event.getWorkoutState() == WorkoutState.WORKOUT_STATE_RUNNING) activateMetrics();
                    else deactivateMetrics();
                }
                @Override
                public void onError(Throwable t) {
                    onError.accept(t instanceof Exception ? (Exception) t : new Exception(t));
                }
                @Override
                public void onCompleted() {}
            });

            started = true;
        } catch (Exception e) {
            shutdown();
            IOException io = e instanceof IOException ? (IOException) e : new IOException(e);
            throw io;
        }
    }

    private void activateMetrics() {
        if (!workoutActive.compareAndSet(false, true)) return;
        Empty empty = Empty.newBuilder().build();

        safePoll(() -> emit(new InclineTelemetry(
                (float) InclineServiceGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(2, TimeUnit.SECONDS).getIncline(empty).getLastInclinePercent())));
        safePoll(() -> emit(new ResistanceTelemetry((float) ResistanceServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(2, TimeUnit.SECONDS).getResistance(empty).getLastResistance())));
        safePoll(() -> emit(new SpeedTelemetry((float) SpeedServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(2, TimeUnit.SECONDS).getSpeed(empty).getLastKph())));
        safePoll(() -> emit(new CadenceTelemetry((float) RpmServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(2, TimeUnit.SECONDS).getRpm(empty).getLastRpm())));
        safePoll(() -> emit(new WattsTelemetry((float) WattsServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(2, TimeUnit.SECONDS).getWatts(empty).getLastWatts())));
        safePoll(() -> emit(new HeartRateTelemetry((float) HeartRateServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(2, TimeUnit.SECONDS).getHeartRate(empty).getLastBpm())));

        InclineServiceGrpc.InclineServiceStub incline = InclineServiceGrpc.newStub(channel);
        ResistanceServiceGrpc.ResistanceServiceStub resistance = ResistanceServiceGrpc.newStub(channel);
        SpeedServiceGrpc.SpeedServiceStub speed = SpeedServiceGrpc.newStub(channel);
        RpmServiceGrpc.RpmServiceStub rpm = RpmServiceGrpc.newStub(channel);
        WattsServiceGrpc.WattsServiceStub watts = WattsServiceGrpc.newStub(channel);
        HeartRateServiceGrpc.HeartRateServiceStub heartRate = HeartRateServiceGrpc.newStub(channel);

        incline.inclineSubscription(empty, observer(v ->
                new InclineTelemetry((float) v.getLastInclinePercent()), "incline"));
        resistance.resistanceSubscription(empty, observer(v ->
                new ResistanceTelemetry((float) v.getLastResistance()), "resistance"));
        speed.speedSubscription(empty, observer(v ->
                new SpeedTelemetry((float) v.getLastKph()), "speed"));
        rpm.rpmSubscription(empty, observer(v ->
                new CadenceTelemetry((float) v.getLastRpm()), "rpm"));
        watts.wattsSubscription(empty, observer(v ->
                new WattsTelemetry((float) v.getLastWatts()), "watts"));
        heartRate.heartRateSubscription(empty, observer(v ->
                new HeartRateTelemetry((float) v.getLastBpm()), "heartRate"));
    }

    private void deactivateMetrics() {
        workoutActive.set(false);
    }

    @Override
    public boolean subscribe(Consumer<Telemetry> listener) {
        this.listener = listener;
        return true;
    }

    private interface Mapper<T> {
        Telemetry map(T value);
    }

    private interface Poll {
        void run() throws Exception;
    }

    private static void safePoll(Poll poll) {
        try {
            poll.run();
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    private <T> StreamObserver<T> observer(Mapper<T> mapper, String metric) {
        return new StreamObserver<T>() {
            @Override
            public void onNext(T value) {
                onLine.accept("glassos: " + metric);
                emit(mapper.map(value));
            }

            @Override
            public void onError(Throwable t) {
                onError.accept(t instanceof Exception ? (Exception) t : new Exception(t));
            }

            @Override
            public void onCompleted() {
            }
        };
    }

    private void emit(Telemetry telemetry) {
        Consumer<Telemetry> l = listener;
        if (l != null) l.accept(telemetry);
    }

    private synchronized void shutdown() {
        if (channel != null) {
            channel.shutdownNow();
            channel = null;
        }
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
