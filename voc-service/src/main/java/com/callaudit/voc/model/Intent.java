package com.callaudit.voc.model;

/**
 * Primary intent classification for customer calls
 */
public enum Intent {
    inquiry,      // Customer is asking questions or seeking information
    complaint,    // Customer is expressing dissatisfaction or issues
    compliment,   // Customer is providing positive feedback
    request,      // Customer is requesting a service or action
    other         // Intent cannot be clearly classified
}
