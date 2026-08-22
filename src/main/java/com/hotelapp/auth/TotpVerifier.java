package com.hotelapp.auth;

public interface TotpVerifier {

    boolean verify(String secretBase32, String code);
}
