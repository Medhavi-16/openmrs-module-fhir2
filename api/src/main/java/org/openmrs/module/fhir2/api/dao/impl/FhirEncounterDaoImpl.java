/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.fhir2.api.dao.impl;

import static org.hibernate.criterion.Projections.property;

import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.NumberParam;
import ca.uhn.fhir.rest.param.ReferenceAndListParam;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.Value;
import org.hibernate.Criteria;
import org.hibernate.criterion.Projections;
import org.openmrs.Encounter;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.dao.FhirEncounterDao;
import org.openmrs.module.fhir2.api.search.param.SearchParameterMap;
import org.springframework.stereotype.Component;

@Component
@Setter(AccessLevel.PACKAGE)
public class FhirEncounterDaoImpl extends BaseFhirDao<Encounter> implements FhirEncounterDao {
	
	@Override
	public List<String> getSearchResultUuids(@Nonnull SearchParameterMap theParams) {
		if (!theParams.getParameters(FhirConstants.LASTN_ENCOUNTERS_SEARCH_HANDLER).isEmpty()) {
			Criteria criteria = getSessionFactory().getCurrentSession().createCriteria(typeToken.getRawType());
			
			setupSearchParams(criteria, theParams);
			
			criteria.setProjection(Projections.projectionList().add(property("uuid")).add(property("encounterDatetime")));
			
			@SuppressWarnings("unchecked")
			List<LastnEncountersResults> results = ((List<Object[]>) criteria.list()).stream()
			        .map(LastnEncountersResults::new).collect(Collectors.toList());
			
			return getTopNRankedUuids(results, getMaxParameter(theParams));
		}
		
		return super.getSearchResultUuids(theParams);
	}
	
	@Override
	protected void setupSearchParams(Criteria criteria, SearchParameterMap theParams) {
		theParams.getParameters().forEach(entry -> {
			switch (entry.getKey()) {
				case FhirConstants.DATE_RANGE_SEARCH_HANDLER:
					entry.getValue().forEach(param -> handleDateRange("encounterDatetime", (DateRangeParam) param.getParam())
					        .ifPresent(criteria::add));
					break;
				case FhirConstants.LOCATION_REFERENCE_SEARCH_HANDLER:
					entry.getValue().forEach(param -> handleLocationReference("l", (ReferenceAndListParam) param.getParam())
					        .ifPresent(l -> criteria.createAlias("location", "l").add(l)));
					break;
				case FhirConstants.PARTICIPANT_REFERENCE_SEARCH_HANDLER:
					entry.getValue().forEach(
					    param -> handleParticipantReference(criteria, (ReferenceAndListParam) param.getParam()));
					break;
				case FhirConstants.PATIENT_REFERENCE_SEARCH_HANDLER:
					entry.getValue()
					        .forEach(param -> handlePatientReference(criteria, (ReferenceAndListParam) param.getParam()));
					break;
				case FhirConstants.COMMON_SEARCH_HANDLER:
					handleCommonSearchParameters(entry.getValue()).ifPresent(criteria::add);
					break;
			}
		});
	}
	
	private int getMaxParameter(SearchParameterMap theParams) {
		return ((NumberParam) theParams.getParameters(FhirConstants.MAX_SEARCH_HANDLER).get(0).getParam()).getValue()
		        .intValue();
	}
	
	private List<String> getTopNRankedUuids(List<LastnEncountersResults> list, int max) {
		
		list.sort((a, b) -> b.getEncounterDatetime().compareTo(a.getEncounterDatetime()));
		List<String> results = new ArrayList<>(list.size());
		int currentRank = 0;
		
		for (int var = 0; var < list.size() && currentRank < max; var++) {
			currentRank++;
			results.add(list.get(var).getUuid());
			Date currentEncounterDate = list.get(var).getEncounterDatetime();
			
			if (var == list.size() - 1) {
				return results;
			}
			
			//Adding all Encounters which have the same Datetime as the current Obs Datetime since they will have the same rank
			Date nextEncounterDate = list.get(var + 1).getEncounterDatetime();
			while (nextEncounterDate.equals(currentEncounterDate)) {
				results.add(list.get(var + 1).getUuid());
				var++;
				
				if (var + 1 == list.size()) {
					return results;
				}
				nextEncounterDate = list.get(var + 1).getEncounterDatetime();
			}
		}
		
		return results;
	}
	
	@Value
	private static class LastnEncountersResults {
		
		private String uuid;
		
		private Date encounterDatetime;
		
		LastnEncountersResults(Object[] encounter) {
			uuid = (String) encounter[0];
			encounterDatetime = (Date) encounter[1];
		}
	}
}
