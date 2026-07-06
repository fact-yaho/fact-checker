package com.yaho.factchecker.domain.claim.service;

import com.yaho.factchecker.domain.claim.dto.command.ClaimCategoryCreateCommand;
import com.yaho.factchecker.domain.claim.dto.command.ClaimCountryCreateCommand;
import com.yaho.factchecker.domain.claim.dto.command.ClaimCreateCommand;
import com.yaho.factchecker.domain.claim.dto.response.ClaimCategoryResponse;
import com.yaho.factchecker.domain.claim.dto.response.ClaimCountryResponse;
import com.yaho.factchecker.domain.claim.dto.response.ClaimResponse;
import com.yaho.factchecker.domain.claim.entity.Claim;
import com.yaho.factchecker.domain.claim.entity.ClaimCategoryMapping;
import com.yaho.factchecker.domain.claim.entity.ClaimCountry;
import com.yaho.factchecker.domain.claim.repository.ClaimRepository;
import com.yaho.factchecker.domain.retrieval.entity.Category;
import com.yaho.factchecker.domain.retrieval.repository.CategoryRepository;
import com.yaho.factchecker.global.exception.BusinessException;
import com.yaho.factchecker.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ClaimService {
    private final ClaimRepository claimRepository;
    private final CategoryRepository categoryRepository;

    public ClaimResponse createClaim(ClaimCreateCommand command) {
        validateCategories(command.categories());

        Claim claim = Claim.builder()
                .factCheckId(command.factCheckId())
                .claimAnalysisAiLogId(command.claimAnalysisAiLogId())
                .originalText(command.originalText())
                .canonicalClaim(command.canonicalClaim())
                .timeScope(command.timeScope())
                .verifiable(command.verifiable())
                .unverifiableReason(command.unverifiableReason())
                .build();

        if (command.countries() != null) {
            for (ClaimCountryCreateCommand countryCommand : command.countries()) {
                ClaimCountry country = ClaimCountry.builder()
                        .name(countryCommand.name())
                        .code(countryCommand.code())
                        .build();

                claim.addCountry(country);
            }
        }

        for (ClaimCategoryCreateCommand categoryCommand : command.categories()) {
            Category category = categoryRepository.findByCategoryName(categoryCommand.category())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

            ClaimCategoryMapping mapping = ClaimCategoryMapping.builder()
                    .category(category)
                    .primaryCategory(categoryCommand.primaryCategory())
                    .build();

            claim.addCategory(mapping);
        }

        Claim savedClaim = claimRepository.save(claim);

        return toResponse(savedClaim);
    }

    private ClaimResponse toResponse(Claim claim) {
        List<ClaimCategoryResponse> categories = claim.getCategories().stream()
                .map(this::toCategoryResponse)
                .toList();

        List<ClaimCountryResponse> countries = claim.getCountries().stream()
                .map(country -> new ClaimCountryResponse(
                        country.getName(),
                        country.getCode()
                ))
                .toList();

        return new ClaimResponse(
                claim.getId(),
                claim.getFactCheckId(),
                claim.getClaimAnalysisAiLogId(),
                claim.getOriginalText(),
                claim.getCanonicalClaim(),
                claim.getTimeScope(),
                claim.isVerifiable(),
                claim.getUnverifiableReason(),
                categories,
                countries
        );
    }

    private ClaimCategoryResponse toCategoryResponse(ClaimCategoryMapping mapping) {
        Category category = mapping.getCategory();

        return new ClaimCategoryResponse(
                category.getCategoryId(),
                category.getCategoryName(),
                mapping.isPrimaryCategory()
        );
    }

    private void validateCategories(List<ClaimCategoryCreateCommand> categories) {
        if (categories == null || categories.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        boolean hasInvalidCategory = categories.stream()
                .anyMatch(category -> category == null || category.category() == null);

        if (hasInvalidCategory) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        long primaryCategoryCount = categories.stream()
                .filter(ClaimCategoryCreateCommand::primaryCategory)
                .count();

        if (primaryCategoryCount != 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
