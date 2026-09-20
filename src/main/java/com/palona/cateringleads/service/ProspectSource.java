package com.palona.cateringleads.service;
import com.palona.cateringleads.model.DiscoveryCandidate;
import com.palona.cateringleads.model.SearchCriteria;
import java.util.List;
public interface ProspectSource {
    List<DiscoveryCandidate> discover(SearchCriteria criteria);
    String sourceName();
}