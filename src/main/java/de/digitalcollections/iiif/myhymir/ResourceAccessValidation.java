
package de.digitalcollections.iiif.myhymir;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdrc.auth.Access;
import io.bdrc.auth.rdf.RdfConstants;
import io.bdrc.iiif.resolver.IdentifierInfo;

public class ResourceAccessValidation {

    private static final String CHINA = "China";
    private static final Logger log = LoggerFactory.getLogger(ResourceAccessValidation.class);

    Access access;
    String accessType;
    boolean fairUse;
    boolean isRestrictedInChina;

    public ResourceAccessValidation(Access access, IdentifierInfo idInfo, String img) {
        super();
        this.access = access;
        accessType = idInfo.getAccessShortName();
        this.isRestrictedInChina = idInfo.isChinaRestricted();
        fairUse = RdfConstants.FAIR_USE.equals(accessType);
        if (fairUse) {
            fairUse = idInfo.isFairUsePublicImage(img);
        }
    }

    public ResourceAccessValidation(Access access, String accessType) {
        super();
        this.access = access;
        this.accessType = accessType;
        fairUse = RdfConstants.FAIR_USE.equals(accessType);
    }

    public boolean isFairUse() {
        return fairUse;
    }

    public boolean isAccessible(HttpServletRequest request) {
        if (access == null) {
            access = new Access();
        }
        boolean accessible = true;
        if (isRestrictedInChina) {
            String test = GeoLocation.getCountryName(request.getHeader("X-Real-IP"));
            log.info("TEST IP from X-Real-IP header: {} and country: {}", request.getHeader("X-Real-IP"), test);
            if (test == null || CHINA.equalsIgnoreCase(test)) {
                // if Geolocation country name is null (i.e throws -for instance- an IP parsing
                // exception)
                // then access is denied
                accessible = false;
            }
        }
        return (accessible && access.hasResourceAccess(accessType) || fairUse);
    }

    public boolean isOpenAccess() {
        return accessType.equals(RdfConstants.OPEN);
    }

    @Override
    public String toString() {
        return "ResourceAccessValidation [access=" + access + ", accessType=" + accessType + ", fairUse=" + fairUse + ", isRestrictedInChina=" + isRestrictedInChina + "]";
    }

}
