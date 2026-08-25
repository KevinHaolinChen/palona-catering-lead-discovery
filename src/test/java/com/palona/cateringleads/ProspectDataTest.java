package com.palona.cateringleads;

import com.palona.cateringleads.model.ClaimKind;
import com.palona.cateringleads.service.ProspectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProspectDataTest {

    @Autowired
    private ProspectService prospectService;

    @Test
    void cachedResultHasAtLeastTenProspects() {
        assertThat(prospectService.findAll()).hasSizeGreaterThanOrEqualTo(10);
    }

    @Test
    void scoresMatchTheirBreakdowns() {
        assertThat(prospectService.findAll())
                .allSatisfy(prospect -> assertThat(prospect.score())
                        .isEqualTo(prospect.scoreBreakdown().total()));
    }

    @Test
    void everyFactHasProvenance() {
        assertThat(prospectService.findAll())
                .flatExtracting(prospect -> prospect.evidence())
                .filteredOn(evidence -> evidence.kind() == ClaimKind.FACT)
                .allSatisfy(evidence -> assertThat(evidence.sourceUrl()).isNotBlank());
    }

    @Test
    void contactPathsDoNotRequireInventedPeople() {
        assertThat(prospectService.findAll())
                .allSatisfy(prospect -> {
                    assertThat(prospect.contact().role()).isNotBlank();
                    assertThat(prospect.contact().channel()).isNotBlank();
                });
    }
}
