package np.ghar.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** When the TV has started: the link for the phone comes up, and Ghar Home takes the screen. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        try { c.startService(new Intent(c, LinkService.class)); } catch (Exception ignored) { }
        try {
            c.startActivity(new Intent(c, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) { }     // newer Android may refuse: there the user makes it the home app
    }
}
