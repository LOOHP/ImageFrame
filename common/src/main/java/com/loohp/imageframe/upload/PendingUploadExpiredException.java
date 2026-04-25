package com.loohp.imageframe.upload;

public class PendingUploadExpiredException extends Exception {

    public PendingUploadExpiredException() {
        super();
    }

    public PendingUploadExpiredException(Throwable cause) {
        super(cause);
    }
}