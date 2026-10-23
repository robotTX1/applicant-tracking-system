package hu.robottx.ats.domain;

import org.springframework.core.io.Resource;

public record DocumentDownload(String filename, String mimeType, Resource resource) {

}
