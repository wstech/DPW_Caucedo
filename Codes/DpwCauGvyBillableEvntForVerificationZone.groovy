/*
 * Copyright (c) 2017 WeServe LLC. All Rights Reserved.
 *
 */
import org.apache.log4j.Level
import org.apache.log4j.Logger

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
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.rules.EventType

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
 * @Set up the groovy job to be scheduled for an hour 22:00 to call this groovy.
 *
 * @Set up General Reference of  Type as "DPWCAUYARDBLK" and  Identifier1 as "YardBlock",Identifier2 as "GRPCODE" and value with list of yardblock in value 1, list of group code in value2.
 *
 */
class DpwCauGvyBillableEvntForVerificationZone extends GroovyApi {

	private static final Logger LOGGER = Logger.getLogger(DpwCauGvyBillableEvntForVerificationZone.class);

	public void execute(Map inParameters) {
		LOGGER.setLevel(Level.DEBUG);
		log("DpwCauGvyBillableEvntForVerificationZone Execution Started");
		long startTime = System.currentTimeMillis();

		//Fetching the yard block or zone,from General Reference.
		GeneralReference generalReference = GeneralReference.findUniqueEntryById("DPWCAUYARDBLK", "YARDBLOCK","GRPCODE",null)


		if (generalReference != null) {
			String blockId = generalReference.getRefValue1() != null ?  generalReference.getRefValue1() : null;
			String groupId = generalReference.getRefValue2() != null ?  generalReference.getRefValue2() : null;
			log("DpwCauGvyBillableEvntForVerificationZone groupId : "+groupId);
			List<String> groupIds   = Arrays.asList(groupId.split(","));
			List<String> blockIdList   = Arrays.asList(blockId.split(","));
			List<Long> groupKeys   = findGrpKeyList(groupIds);
			List<Unit> unitList = findUnitsToProcess(groupKeys);
			if(unitList != null) {
				for(Unit currentUnit : unitList) {
					log("DpwCauGvyBillableEvntForVerificationZone currentUnit : "+currentUnit);
					UnitFacilityVisit ufv = currentUnit.getUnitActiveUfv();
					log("DpwCauGvyBillableEvntForVerificationZone ufv : "+ufv);

					if(ufv != null && (UfvTransitStateEnum.S40_YARD.equals(ufv.getUfvTransitState()))) {

						if(ufv.getUfvLastKnownPosition()  != null && blockIdList.contains(ufv.getUfvLastKnownPosition().getBlockName())){
							log("DpwCauGvyBillableEvntForVerificationZone  yardBlockId matched record an event: "+ufv.getUfvLastKnownPosition().getBlockName());
							Group grp = currentUnit.getUnitRouting()  != null ? currentUnit.getUnitRouting().getRtgGroup() : null;
							String grpId = null;
							if(grp != null){
								grpId = grp.getGrpId();
							}

							recordEvent("NSWVERIF", currentUnit, "Unit from Verification Zone :" + ufv.getUfvLastKnownPosition().getBlockName() +" with the Specified Group code :"+ grpId);
						}
					}
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
		List<Long> grpKeyList = new ArrayList();
		if(groupIds != null){
			for(String grpId : groupIds) {
				Group grp = Group.findGroup(grpId)
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

	private List<Unit> findUnitsToProcess(List<Long> groupKeys){
		DomainQuery dq = QueryUtils.createDomainQuery("Unit")
				.addDqPredicate(PredicateFactory.eq(UnitField.UNIT_VISIT_STATE, UnitVisitStateEnum.ACTIVE))
				.addDqPredicate(PredicateFactory.eq(UnitField.UNIT_CATEGORY, UnitCategoryEnum.IMPORT))
				.addDqPredicate(PredicateFactory.eq(UnitField.UNIT_FREIGHT_KIND, FreightKindEnum.FCL))
				.addDqPredicate(PredicateFactory.in(UnitField.UNIT_RTG_GROUP, groupKeys));
		List<Unit> unitList = Roastery.getHibernateApi().findEntitiesByDomainQuery(dq);
		return unitList;
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
