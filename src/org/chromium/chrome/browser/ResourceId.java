



package org.chromium.chrome.browser;

import org.chromium.chrome.R;

public class ResourceId {
    public static int mapToDrawableId(int enumeratedId) {
        int[] resourceList = {


0,


R.drawable.infobar_geolocation,
R.drawable.infobar_didyoumean,
R.drawable.infobar_autofill,
R.drawable.infobar_autologin,
R.drawable.infobar_cookie,
R.drawable.infobar_desktop_notifications,

R.drawable.infobar_incomplete,
R.drawable.infobar_camera,
R.drawable.infobar_microphone,
R.drawable.infobar_multiple_downloads,

R.drawable.infobar_plugin_crashed,

R.drawable.infobar_plugin,
R.drawable.infobar_restore,
R.drawable.infobar_savepassword,
R.drawable.infobar_warning,
R.drawable.infobar_theme,
R.drawable.infobar_translate,


R.drawable.controlled_setting_mandatory_large,

R.drawable.pageinfo_bad,
R.drawable.pageinfo_good,
R.drawable.pageinfo_info,
R.drawable.pageinfo_warning_major,

R.drawable.pageinfo_warning_minor,
        };
        if (enumeratedId >= 0 && enumeratedId < resourceList.length) {
            return resourceList[enumeratedId];
        }
        assert false : "enumeratedId '" + enumeratedId + "' was out of range.";
        return R.drawable.missing;
    }
}
