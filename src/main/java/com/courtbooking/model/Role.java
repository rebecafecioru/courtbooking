package com.courtbooking.model;

public enum Role {
    PUBLIC_USER,   // can browse and book courts
    ORG_ADMIN,     // manages one organisation's venues/courts/slots
    SUPER_ADMIN    // platform-level admin
}
