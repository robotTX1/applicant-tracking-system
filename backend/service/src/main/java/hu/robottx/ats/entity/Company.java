package hu.robottx.ats.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "companies")
public class Company extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", length = 100, nullable = false, unique = true)
    private String name;

    @Column(name = "founding_date", nullable = false)
    private LocalDate foundingDate;

    @Column(name = "website_url", length = 2000, nullable = false)
    private String websiteUrl;

    @Column(name = "description", length = 2000, nullable = false)
    private String description;

}
