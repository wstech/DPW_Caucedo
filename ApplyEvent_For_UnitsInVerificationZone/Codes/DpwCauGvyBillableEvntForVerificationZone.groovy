/*
 * Copyright (c) 2017 WeServe LLC. All Rights Reserved.
 *
 */


import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.atoms.FreightKindEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.reference.Group
import com.navis.framework.business.Roastery
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.InventoryEntity
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.rules.EventType
import org.apache.log4j.Level
import org.apache.log4j.Logger

/*
 *
 * @Author <a href="mailto:kgopinath@weservetech.com">Gopinath K</a>, 18/Sep/2017
 *
 * Requirements : This groovy is used to record a NSWVERIF billable event, while the job triggers this groovy and
 * if it found the matching unit with the group code and yard block mention in the general reference.
 *
 * @Inclusion Location	: Incorporated as a groovy plugin extending GroovyApi as mention below:
 *
 * Deployment Steps:
 *	a) Administration -> System -> Groovy Plugins
 *	b) Click on + (Add) Button
 *	c) Add as groovy code - DpwCauGvyBillableEvntForVerificationZone
 *	d) Paste the groovy code and click on save
 *
 * @ Set up the groovy job to be scheduled for an hour 22:00 to call this groovy.
 *
 * @ Set up General Reference of  Type as "DPWCAUYARDBLK" and  Identifier1 as "YARDBLOCK",Identifier2 as "GRPCODE",Identifier3 as "EVENTID" and value with list of yardblock in value 1 and value2, list of group code in value3 and event id in value 4.
 *
 */
class DpwCauGvyBillableEvntForVerificationZone extends GroovyApi {

	private static final Logger LOGGER = Logger.getLogger(DpwCauGvyBillableEvntForVerificationZone.class);

	public void execute(Map inParameters) {
		LOGGER.setLevel(Level.DEBUG);
		log("DpwCauGvyBillableEvntForVerificationZone Execution Started");
		long startTime = System.currentTimeMillis();

		//Fetching the yard block or zone and list of group id from General Reference.
		GeneralReference generalReference = GeneralReference.findUniqueEntryById("DPWCAUYARDBLK","YARDBLOCK","GRPCODE","EVENTID");

		if (generalReference != null) {
			StringBuffer blockId = new StringBuffer();
			blockId.append(generalReference.getRefValue1() != null ? generalReference.getRefValue1() : null);
			if (generalReference.getRefValue2() != null) {
				blockId.append(",");
				blockId.append(generalReference.getRefValue2());
			}

			String groupId = generalReference.getRefValue3() != null ? generalReference.getRefValue3() : null;
			String eventId = generalReference.getRefValue4() != null ? generalReference.getRefValue4() : null;


			if (blockId == null || groupId == null || eventId == null) {
				log("DpwCauGvyBillableEvntForVerificationZone general reference configuration is invalid 1) Block Id : " + blockId + "\n 2) Group Id : " + groupId + " 3) EventId : " + eventId);
				return;
			}
			List<String> groupIds = Arrays.asList(groupId.split(","));
			List<String> blockIdList = Arrays.asList(blockId.toString().split(","));
			if (groupIds != null && groupIds.size() > 0 && blockIdList != null && blockIdList.size() > 0) {
				List<Long> groupKeys = findGrpKeyList(groupIds);
				// Fetching the list of units to process based on the groupkey
				List<UnitFacilityVisit> ufvList = groupKeys != null ? findUnitsToProcess(groupKeys) : null;
				if (ufvList != null) {
					for (UnitFacilityVisit ufv : ufvList) {
						if (ufv != null) {
							if (ufv.getUfvLastKnownPosition() != null && blockIdList != null && blockIdList.contains(ufv.getUfvLastKnownPosition().getBlockName())) {
								Group grp = ufv.getUfvUnit() !=null && ufv.getUfvUnit().getUnitRouting() != null ? ufv.getUfvUnit().getUnitRouting().getRtgGroup() : null;
								String grpId = null;
								if (grp != null) {
									grpId = grp.getGrpId();
								}
								// record an billable event "NSWVERIF" once it satisifies the above condition (Matches the zone in YARD and with the group code)
								recordEvent(eventId, ufv.getUfvUnit(), "Unit from Verification Zone :" + ufv.getUfvLastKnownPosition().getBlockName() + " with the Specified Group code :" + grpId);
							}
						}
					}
				} else {
					log("DpwCauGvyBillableEvntForVerificationZone Unit List is empty for this block / group code");
				}
			}
		}

		long endTime = System.currentTimeMillis();
		log("DpwCauGvyBillableEvntForVerificationZone Execution Completed in :" + (endTime - startTime) / 1000 + " secs.");
	}


	/*
	 * This method is used to retrive the gkey for the list of group codes.
	 * @param List<String> groupKeys
	 * @return List<Long>
	 */

	private  List<Long> findGrpKeyList(List<String> groupIds){
		List<Long> grpKeyList = null;
		if(groupIds != null){
			grpKeyList = new ArrayList();
			for(String grpId : groupIds) {
				Group grp = Group.findGroup(grpId)
				if (grp!=null)
					grpKeyList.add(grp.getGrpGkey());
			}
		}
		return grpKeyList;
	}

	/*
	 * This method is used to retrive the list of Units with the following group code, also in Visit State as ACTIVE,
	 * Transit State as YARD, Category as IMPORT and FreightKind as FCL.
	 * @param List<long> groupKeys
	 * @return List<Unit>
	 */

	private List<UnitFacilityVisit> findUnitsToProcess(List<Long> groupKeys){
		DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT_FACILITY_VISIT)
				.addDqPredicate(PredicateFactory.eq(UnitField.UFV_VISIT_STATE, UnitVisitStateEnum.ACTIVE))
				.addDqPredicate(PredicateFactory.eq(UnitField.UFV_TRANSIT_STATE, UfvTransitStateEnum.S40_YARD))
				.addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_CATEGORY, UnitCategoryEnum.IMPORT))
				.addDqPredicate(PredicateFactory.eq(UnitField.UFV_FREIGHT_KIND, FreightKindEnum.FCL))
				.addDqPredicate(PredicateFactory.in(UnitField.UFV_GROUP, groupKeys));
		List<UnitFacilityVisit> ufvList = Roastery.getHibernateApi().findEntitiesByDomainQuery(dq);
		return ufvList;
	}


	/*
	 * This method is used to record an event on Unit.
	 * @param inEventTypeId, @param inUnit
	 * @return IEvent recorded in Unit
	 */
	public void recordEvent(String inEventTypeId, Unit inUnit, String inNotes) {
		final ServicesManager srvcMgr = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID);
		EventType eventType = EventType.findEventType(inEventTypeId);
		if (eventType != null) {
			srvcMgr.recordEvent(eventType, inNotes, null, null, inUnit)
		}
	}

}
