package com.smartAiUniversityAssistant.seniorproject.feature.campus.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.exception.CampusFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.mapper.CampusMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.repository.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.service.CampusCatalogService;

/** Categories and facilities. Neither can be deactivated while a live place or room depends on it. */
@Service
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
public class CampusCatalogServiceImpl implements CampusCatalogService {
    private final CampusCategoryRepository categories;
    private final CampusFacilityRepository facilities;
    private final CampusPlaceRepository places;
    private final CampusMapper mapper;
    private final CampusWriteSupport support;

    public CampusCatalogServiceImpl(CampusCategoryRepository categories, CampusFacilityRepository facilities, CampusPlaceRepository places,
            CampusMapper mapper, CampusWriteSupport support) {
        this.categories = categories;
        this.facilities = facilities;
        this.places = places;
        this.mapper = mapper;
        this.support = support;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public List<CampusCategoryResponse> categories() {
        return categories.findByDeletedFalseOrderByCategoryNameAscIdAsc().stream().map(mapper::category).toList();
    }

    @Override @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')") @Transactional(timeout = 15)
    public CampusCategoryResponse createCategory(AuthenticatedUser actor, CampusCategoryRequest request) {
        var category = new CampusCategory();
        apply(category, request, 0L);
        category.setActive(request.isActive() == null || request.isActive());
        category.setCreatedBy(actor.userId());
        category.setCreatedAt(support.now());
        try {
            return mapper.category(categories.saveAndFlush(category));
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
    }

    @Override @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')") @Transactional(timeout = 15)
    public CampusCategoryResponse updateCategory(AuthenticatedUser actor, long id, CampusCategoryRequest request) {
        support.lockTimeout();
        CampusCategory category = categories.findLiveForUpdate(id).orElseThrow(CampusFailure::categoryNotFound);
        apply(category, request, id);
        if (Boolean.FALSE.equals(request.isActive()) && category.isActive() && places.existsByCategoryIdAndDeletedFalse(id))
            throw CampusFailure.inUse("The category is used by campus places. Move them to another category first.");
        if (request.isActive() != null) category.setActive(request.isActive());
        category.setUpdatedBy(actor.userId());
        category.setUpdatedAt(support.now());
        try {
            categories.flush();
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
        return mapper.category(category);
    }

    private void apply(CampusCategory category, CampusCategoryRequest request, long id) {
        String code = support.referenceCode(request.categoryCode());
        if (categories.existsByCategoryCodeAndIdNot(code, id)) throw CampusFailure.duplicate("The category code");
        category.setCategoryCode(code);
        category.setCategoryName(support.required(request.categoryName()));
        category.setMobileGroup(request.mobileGroup());
        category.setDescription(support.optional(request.description()));
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public List<CampusFacilityResponse> facilities(boolean activeOnly) {
        var rows = activeOnly ? facilities.findByActiveTrueOrderByFacilityNameAscIdAsc() : facilities.findAllByOrderByFacilityNameAscIdAsc();
        return rows.stream().map(mapper::facility).toList();
    }

    @Override @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')") @Transactional(timeout = 15)
    public CampusFacilityResponse createFacility(AuthenticatedUser actor, CampusFacilityRequest request) {
        var facility = new CampusFacility();
        apply(facility, request, 0L);
        facility.setActive(request.isActive() == null || request.isActive());
        facility.setCreatedBy(actor.userId());
        facility.setCreatedAt(support.now());
        try {
            return mapper.facility(facilities.saveAndFlush(facility));
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
    }

    @Override @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')") @Transactional(timeout = 15)
    public CampusFacilityResponse updateFacility(AuthenticatedUser actor, long id, CampusFacilityRequest request) {
        support.lockTimeout();
        CampusFacility facility = facilities.findForUpdate(id).orElseThrow(CampusFailure::facilityNotFound);
        apply(facility, request, id);
        if (Boolean.FALSE.equals(request.isActive()) && facility.isActive() && facilities.isInUse(id))
            throw CampusFailure.inUse("The facility is used by rooms or places. Remove it from them first.");
        if (request.isActive() != null) facility.setActive(request.isActive());
        facility.setUpdatedBy(actor.userId());
        facility.setUpdatedAt(support.now());
        try {
            facilities.flush();
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
        return mapper.facility(facility);
    }

    private void apply(CampusFacility facility, CampusFacilityRequest request, long id) {
        String code = support.referenceCode(request.facilityCode());
        if (facilities.existsByFacilityCodeAndIdNot(code, id)) throw CampusFailure.duplicate("The facility code");
        facility.setFacilityCode(code);
        facility.setFacilityName(support.required(request.facilityName()));
        facility.setDescription(support.optional(request.description()));
    }
}
