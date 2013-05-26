package org.chromium.chrome.browser;
public class ResourceId {
    public static int mapToDrawableId(int enumeratedId) {
        int[] resourceList = {
0,
        };
        if (enumeratedId >= 0 && enumeratedId < resourceList.length) {
            return resourceList[enumeratedId];
        }
        assert false : "enumeratedId '" + enumeratedId + "' was out of range.";
        return 0;
    }
}
