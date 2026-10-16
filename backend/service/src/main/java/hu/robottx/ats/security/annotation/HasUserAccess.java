package hu.robottx.ats.security.annotation;

import static hu.robottx.ats.security.constant.AuthorizationConstants.HAS_USER_ACCESS;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

@Documented
@PreAuthorize(HAS_USER_ACCESS)
@Retention(RetentionPolicy.RUNTIME)
@Target(value = ElementType.METHOD)
public @interface HasUserAccess {
}
