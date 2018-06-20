package io.bdrc.pdf.presentation.models;


public enum LicenseType {
    COPYRIGHTED("http://purl.bdrc.io/resource/LicenseCopyrighted"),
    PUBLIC_DOMAIN("http://purl.bdrc.io/resource/LicensePublicDomain"),
    MIXED("http://purl.bdrc.io/resource/LicenseMixed");
    
    private String uri;
    
    private LicenseType(String uri) {
        this.setUri(uri);
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public static LicenseType fromString(String license) {
        for (LicenseType lt : LicenseType.values()) {
          if (lt.uri.equals(license)) {
            return lt;
          }
        }
        return null;
      }
}
