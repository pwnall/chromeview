package org.chromium.content.browser;
public class PageTransitionTypes {
public static final int PAGE_TRANSITION_LINK = 0;
public static final int PAGE_TRANSITION_TYPED = 1;
public static final int PAGE_TRANSITION_AUTO_BOOKMARK = 2;
public static final int PAGE_TRANSITION_AUTO_SUBFRAME = 3;
public static final int PAGE_TRANSITION_MANUAL_SUBFRAME = 4;
public static final int PAGE_TRANSITION_GENERATED = 5;
public static final int PAGE_TRANSITION_AUTO_TOPLEVEL = 6;
public static final int PAGE_TRANSITION_FORM_SUBMIT = 7;
public static final int PAGE_TRANSITION_RELOAD = 8;
public static final int PAGE_TRANSITION_KEYWORD = 9;
public static final int PAGE_TRANSITION_KEYWORD_GENERATED = 10;
public static final int PAGE_TRANSITION_LAST_CORE = PAGE_TRANSITION_KEYWORD_GENERATED;
public static final int PAGE_TRANSITION_CORE_MASK = 0xFF;
public static final int PAGE_TRANSITION_BLOCKED = 0x00800000;
public static final int PAGE_TRANSITION_FORWARD_BACK = 0x01000000;
public static final int PAGE_TRANSITION_FROM_ADDRESS_BAR = 0x02000000;
public static final int PAGE_TRANSITION_HOME_PAGE = 0x04000000;
public static final int PAGE_TRANSITION_FROM_INTENT = 0x08000000;
public static final int PAGE_TRANSITION_CHAIN_START = 0x10000000;
public static final int PAGE_TRANSITION_CHAIN_END = 0x20000000;
public static final int PAGE_TRANSITION_CLIENT_REDIRECT = 0x40000000;
public static final int PAGE_TRANSITION_SERVER_REDIRECT = 0x80000000;
public static final int PAGE_TRANSITION_IS_REDIRECT_MASK = 0xC0000000;
public static final int PAGE_TRANSITION_QUALIFIER_MASK = 0xFFFFFF00;
}
