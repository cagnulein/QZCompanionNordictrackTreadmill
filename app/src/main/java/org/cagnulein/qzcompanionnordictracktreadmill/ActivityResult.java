import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AndroidActivityResultReceiver {

    private Context context;

    public AndroidActivityResultReceiver(Context context) {
        this.context = context;
    }

    public void handleActivityResult(int receiverRequestCode, int resultCode, Intent data) {
        Log.d("AndroidActivityResultReceiver", "handleActivityResult: " + receiverRequestCode + " " + resultCode);
        MediaProjection.startService(context, resultCode, data);
    }
}
