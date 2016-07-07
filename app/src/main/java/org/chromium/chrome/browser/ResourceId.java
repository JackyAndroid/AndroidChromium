package org.chromium.chrome.browser;
import org.chromium.chrome.R;
public class ResourceId {
    public static int mapToDrawableId(int enumeratedId) {
        int[] resourceList = {
0,
R.drawable.infobar_autofill_cc,
R.drawable.infobar_camera,
R.drawable.infobar_microphone,
R.drawable.infobar_midi,
R.drawable.infobar_multiple_downloads,
R.drawable.infobar_savepassword,
R.drawable.infobar_warning,
R.drawable.infobar_translate,
R.drawable.infobar_blocked_popups,
R.drawable.infobar_protected_media_identifier,
R.drawable.infobar_desktop_notifications,
R.drawable.infobar_geolocation,
R.drawable.infobar_restore,
R.drawable.infobar_fullscreen,
R.drawable.pageinfo_good,
R.drawable.pageinfo_warning,
R.drawable.pageinfo_bad,
R.drawable.pageinfo_warning,
R.drawable.pageinfo_warning,
R.drawable.pageinfo_warning,
R.drawable.amex_card,
R.drawable.discover_card,
R.drawable.generic_card,
R.drawable.mc_card,
R.drawable.visa_card,
android.R.drawable.ic_menu_camera,
org.chromium.chrome.R.drawable.ic_photo_camera,
R.drawable.cvc_icon,
R.drawable.cvc_icon_amex,
org.chromium.chrome.R.drawable.ic_settings,
        };
        if (enumeratedId >= 0 && enumeratedId < resourceList.length) {
            return resourceList[enumeratedId];
        }
        assert false : "enumeratedId '" + enumeratedId + "' was out of range.";
        return R.drawable.missing;
    }
}
