
/*
 * Copyright (c) 2017 WeServe LLC. All Rights Reserved.
 *
 */
import org.apache.log4j.Logger

import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent

/*
 *
 * @Author <a href="mailto:kgopinath@weservetech.com">Gopinath K</a>, 24/May/2017
 *
 * Requirements : This groovy is used to record a SEAL_FIX event, when any seal starting with GT recorded against container after discharging
 *               from vessel and prior to delivery.
 *
 * @Inclusion Location	: Incorporated as a code extension of the type GENERAL_NOTICE_CODE_EXTENSION as mention below.
 *
 * Deployment Steps:
 *	a) Administration -> System -> Code Extension
 *	b) Click on + (Add) Button
 *	c) Add as GENERAL_NOTICES_CODE_EXTENSION and code extension name as DpwCauGvyUpdateFlexField
 *	d) Paste the groovy code and click on save
 *
 * @Set up General Notice for event type "UNIT_ACTIVATE" on Unit Entity then execute this code extension (DpwCauGvyUpdateFlexField).
 *
 */

class DpwCauGvyUpdateFlexField extends AbstractGeneralNoticeCodeExtension {
	private static final Logger LOGGER = Logger.getLogger(DpwCauGvyUpdateFlexField.class);

	public void execute(GroovyEvent inEvent) {
		log("DpwCauGvyUpdateFlexField Execution Started")
		long startTime = System.currentTimeMillis();
		if (inEvent == null) {
			return;
		}
		Event event = inEvent.getEvent();
		if (event != null) {
			Unit unit = (Unit) inEvent.getEntity();
			if (unit != null && unitCategoryList.contains(unit.getUnitCategory()) && unit.getUnitActiveUfv() != null) {
				if(UfvTransitStateEnum.S20_INBOUND.equals(unit.getUnitActiveUfv().getUfvTransitState())) {
					if(unit.getUnitFlexString05() == null && unit.getUnitFlexString12() == null) {
						if(unit.getInboundCv() != null && unit.getInboundCv().getCvCvd() != null && CarrierVisitPhaseEnum.INBOUND.equals(unit.getInboundCv().getCvVisitPhase())){
							unit.setUnitFlexString05(unit.getInboundCv().getCvId());
							if((unit.getInboundCv().getCvCvd() != null && unit.getInboundCv().getCvCvd().getCvdService() != null)){
								unit.setUnitFlexString12(unit.getInboundCv().getCvCvd().getCvdService().getSrvcId());
							}
						}
					}
				}
			}
			long endTime = System.currentTimeMillis();
			log("DpwCauGvyUpdateFlexField Execution Completed in :" + (endTime - startTime) / 1000 + " secs.");
		}
	}


	private static final List<UnitCategoryEnum> unitCategoryList = new ArrayList();
	static {
		unitCategoryList.add(UnitCategoryEnum.IMPORT);
		unitCategoryList.add(UnitCategoryEnum.TRANSSHIP);
		unitCategoryList.add(UnitCategoryEnum.THROUGH);
	}
}