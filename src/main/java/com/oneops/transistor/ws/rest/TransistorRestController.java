/*******************************************************************************
 *  
 *   Copyright 2015 Walmart, Inc.
 *  
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *  
 *       http://www.apache.org/licenses/LICENSE-2.0
 *  
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  
 *******************************************************************************/
package com.oneops.transistor.ws.rest;

import com.oneops.cms.cm.domain.CmsCI;
import com.oneops.cms.cm.domain.CmsCIRelation;
import com.oneops.cms.dj.domain.CmsRfcCI;
import com.oneops.cms.exceptions.CIValidationException;
import com.oneops.cms.exceptions.CmsBaseException;
import com.oneops.cms.exceptions.DJException;
import com.oneops.cms.simple.domain.CmsCIRelationSimple;
import com.oneops.cms.simple.domain.CmsCISimple;
import com.oneops.cms.simple.domain.CmsRfcCISimple;
import com.oneops.cms.simple.domain.CmsRfcRelationSimple;
import com.oneops.cms.util.CmsError;
import com.oneops.cms.util.CmsUtil;
import com.oneops.transistor.domain.IaasRequest;
import com.oneops.transistor.exceptions.DesignExportException;
import com.oneops.transistor.exceptions.TransistorException;
import com.oneops.transistor.export.domain.DesignExportSimple;
import com.oneops.transistor.service.*;
import org.apache.log4j.Logger;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;



@Controller
public class TransistorRestController extends AbstractRestController {

	static Logger logger = Logger.getLogger(TransistorRestController.class);
	
	private ManifestManager manifestManager;
	private BomEnvManager envManager;
	private IaasManager iaasManager;
	private DesignManager dManager;
	private BomAsyncProcessor baProcessor;
	private ManifestAsyncProcessor maProcessor;
	private CmsUtil util = new CmsUtil();

	public void setMaProcessor(ManifestAsyncProcessor maProcessor) {
		this.maProcessor = maProcessor;
	}

	public void setBaProcessor(BomAsyncProcessor baProcessor) {
		this.baProcessor = baProcessor;
	}

	public void setIaasManager(IaasManager iaasManager) {
		this.iaasManager = iaasManager;
	}

	public void setManifestManager(ManifestManager manifestManager) {
		this.manifestManager = manifestManager;
	}

	public void setEnvManager(BomEnvManager envManager) {
		this.envManager = envManager;
	}
	
	public void setdManager(DesignManager dManager) {
		this.dManager = dManager;
	}
	
	@ExceptionHandler(TransistorException.class)
	@ResponseBody
	public void handleExceptions(TransistorException e, HttpServletResponse response) throws IOException {
		logger.error(e);
		sendError(response,HttpServletResponse.SC_BAD_REQUEST,e);
	}

	@ExceptionHandler(DesignExportException.class)
	@ResponseBody
	public void handleExceptions(DesignExportException e, HttpServletResponse response) throws IOException {
		logger.error(e);
		sendError(response,HttpServletResponse.SC_BAD_REQUEST,e);
	}
	
	@ExceptionHandler(DJException.class)
	public void handleDJExceptions(DJException e, HttpServletResponse response) throws IOException {
		logger.error(e);
		sendError(response,HttpServletResponse.SC_BAD_REQUEST,e);
	}
	
	@ExceptionHandler(CIValidationException.class)
	public void handleCIValidationExceptions(CIValidationException e, HttpServletResponse response) throws IOException {
		logger.error(e);
		sendError(response,HttpServletResponse.SC_BAD_REQUEST,e);
	}
	
	
	@RequestMapping(value="/assemblies/{assemblyId}/platforms", method = RequestMethod.POST)
	@ResponseBody
	public Map<String,Long> generateDesign(
			@PathVariable long assemblyId,
			@RequestBody CmsRfcCISimple platRfcSimple,
			@RequestHeader(value="X-Cms-User", required = false)  String userId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){

		if (userId == null) userId = "oneops-system";
		
		long startTime = System.currentTimeMillis(); 
		
		CmsRfcCI platRfc = util.custRfcCISimple2RfcCI(platRfcSimple);
		try {
			long platformCiId = dManager.generatePlatform(platRfc, assemblyId, userId, scope);  
			Map<String,Long> result = new HashMap<String,Long>(1); 
			result.put("platformCiId", platformCiId);
	
			long tookTime = System.currentTimeMillis() - startTime;
			logger.debug("Time to generate Design - " + tookTime);
	
			return result;
		} catch (DataIntegrityViolationException dive) {
			if (dive instanceof DuplicateKeyException) {
				throw new CIValidationException(CmsError.CMS_DUPCI_NAME_ERROR, dive.getMessage());
			} else {
				throw new TransistorException(CmsError.CMS_EXCEPTION, dive.getMessage());
			}
		} catch (CmsBaseException te) {
			logger.error(te);
			te.printStackTrace();
			throw te;
		}
	}

	@RequestMapping(value="/assemblies/{assemblyId}/platforms/{platformId}", method = RequestMethod.DELETE)
	@ResponseBody
	public Map<String,Long> deletePlatform(
			@PathVariable long assemblyId,
			@PathVariable long platformId,
			@RequestHeader(value="X-Cms-User", required = false)  String userId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){

		try {
			long startTime = System.currentTimeMillis(); 
			
			long platformCiId = dManager.deletePlatform(platformId, userId, scope);  
			
			Map<String,Long> result = new HashMap<String,Long>(1); 
			result.put("platformCiId", platformCiId);
	
			long tookTime = System.currentTimeMillis() - startTime;
			logger.debug("Time to generate Design - " + tookTime);
	
			return result;
		} catch (CmsBaseException te) {
			logger.error(te);
			te.printStackTrace();
			throw te;
		}

	}

	
	@RequestMapping(value="/platforms/{fromPlatformId}/clone", method = RequestMethod.POST)
	@ResponseBody
	public Map<String,Long> clonePlatform(
			@PathVariable long fromPlatformId,
			@RequestBody CmsRfcCISimple platRfcSimple,
			@RequestHeader(value="X-Cms-User", required = false)  String userId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){

		if (userId == null) userId = "oneops-system";
		try {
			long startTime = System.currentTimeMillis(); 
			
			CmsRfcCI platRfc = util.custRfcCISimple2RfcCI(platRfcSimple);
			
			long platformId = dManager.clonePlatform(platRfc, null, fromPlatformId, userId, scope);  
			
			Map<String,Long> result = new HashMap<String,Long>(1); 
			result.put("platformCiId", platformId);
	
			long tookTime = System.currentTimeMillis() - startTime;
			logger.debug("Time to generate Design - " + tookTime);
	
			return result;
		} catch (DataIntegrityViolationException dive) {
			if (dive instanceof DuplicateKeyException) {
				throw new CIValidationException(CmsError.CMS_DUPCI_NAME_ERROR, dive.getMessage());
			} else {
				throw new TransistorException(CmsError.CMS_EXCEPTION, dive.getMessage());
			}
		} catch (CmsBaseException te) {
			logger.error(te);
			te.printStackTrace();
			throw te;
		}
	}

	@RequestMapping(value="/assemblies/{fromAssemblyId}/clone", method = RequestMethod.POST)
	@ResponseBody
	public Map<String,Long> cloneAssembly(
			@PathVariable long fromAssemblyId,
			@RequestBody CmsCISimple targetCISimple,
			@RequestHeader(value="X-Cms-User", required = false)  String userId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){

		if (userId == null) userId = "oneops-system";
		try {
			long startTime = System.currentTimeMillis(); 
			
			if (targetCISimple.getCiAttributes().get("description") == null) {
				targetCISimple.addCiAttribute("description", null);
			}
			
			CmsCI targetCI = util.custCISimple2CI(targetCISimple, null);
			
			long resultCiId = 0;
			if ("account.Assembly".equals(targetCI.getCiClassName())) {
				resultCiId = dManager.cloneAssembly(targetCI, fromAssemblyId, userId, scope);
			} else if ("account.Design".equals(targetCI.getCiClassName())) {
				resultCiId = dManager.saveAssemblyAsCatalog(targetCI, fromAssemblyId, userId, scope);
			} else {
				throw new TransistorException(CmsError.TRANSISTOR_BAD_CLASS_NAME, "Bad class name");
			}
			
			Map<String,Long> result = new HashMap<String,Long>(1); 
			result.put("resultCiId", resultCiId);
	
			long tookTime = System.currentTimeMillis() - startTime;
			logger.debug("Time to generate Assembly/Catalog - " + tookTime);
	
			return result;
		} catch (DataIntegrityViolationException dive) {
			if (dive instanceof DuplicateKeyException) {
				throw new CIValidationException(CmsError.CMS_DUPCI_NAME_ERROR, dive.getMessage());
			} else {
				throw new TransistorException(CmsError.CMS_EXCEPTION, dive.getMessage());
			}
		} catch (CmsBaseException te) {
			logger.error(te);
			te.printStackTrace();
			throw te;
		}
	}

	@RequestMapping(value="/assemblies/{assemblyId}/export", method = RequestMethod.GET)
	@ResponseBody
	public DesignExportSimple exportDesign(
			@PathVariable long assemblyId,
			@RequestHeader(value="X-Cms-User", required = false)  String userId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){

		if (userId == null) userId = "oneops-system";
		try {
			return dManager.exportDesign(assemblyId);
		}  catch (CmsBaseException te) {
			logger.error(te);
			te.printStackTrace();
			throw te;
		}
	}

	@RequestMapping(value="/assemblies/{assemblyId}/populateOwner", method = RequestMethod.GET)
	@ResponseBody
	public String populateOwner(
			@PathVariable long assemblyId,
			@RequestHeader(value="X-Cms-User", required = false)  String userId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){

		if (userId == null) userId = "oneops-system";
		try {
			dManager.updateOwner(assemblyId);
			return "All Done";
		}  catch (CmsBaseException te) {
			logger.error(te);
			te.printStackTrace();
			throw te;
		}
	}
	
	
	@RequestMapping(value="/assemblies/{assemblyId}/import", method = RequestMethod.POST)
	@ResponseBody
	public Map<String,String> importDesign(
			@RequestBody DesignExportSimple designExport,
			@PathVariable long assemblyId,
			@RequestHeader(value="X-Cms-User", required = false)  String userId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){

		if (userId == null) userId = "oneops-system";
		
		dManager.importDesign(assemblyId, userId, scope, designExport);
		
		Map<String, String> result = new HashMap<String,String>(1); 
		result.put("result", "success");
		return result;
	}
	
	@RequestMapping(value="/environments/{envId}", method = RequestMethod.PUT)
	@ResponseBody
	public Map<String,String> generateManifest(
			@PathVariable long envId,
			@RequestBody Map<String,String> platModes,
			@RequestHeader(value="X-Cms-User", required = false)  String userId,
			HttpServletResponse response){

		try {
			if (userId == null) userId = "oneops-system";
			
			long startTime = System.currentTimeMillis(); 
			
			//long releaseId = manifestManager.generateEnvManifest(envId, userId, platModes);
			long releaseId = maProcessor.generateEnvManifest(envId, userId, platModes);
			
			Map<String,String> result = new HashMap<String,String>(); 
			result.put("releaseId", String.valueOf(releaseId));
	
			long tookTime = System.currentTimeMillis() - startTime;
			logger.debug("Time to generate Manifest - " + tookTime);
	
			return result;
		} catch (TransistorException te) {
			if (te.getErrorCode() == CmsError.TRANSISTOR_OPEN_MANIFEST_RELEASE) {
				response.setStatus( HttpServletResponse.SC_BAD_REQUEST);
				Map<String, String> result = new HashMap<String,String>(1); 
				result.put("releaseId", "0");
				result.put("message", te.getMessage());
				return result;
			}
			logger.error("", te);
			throw te;
		} catch (CmsBaseException te) {
			logger.error(te);
			te.printStackTrace();
			throw te;
		}
	}
	
	
	@RequestMapping(value="environments/{envId}/deployments", method = RequestMethod.POST)
	@ResponseBody
	public Map<String,Long> generateBom(
			@PathVariable long envId,
			@RequestBody Map<String,String> params,
			@RequestHeader(value="X-Cms-User", required = false)  String userId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){
		try {
			if (userId == null) userId = "oneops-system";
			String desc = params.get("description");
			Set<Long> excludePlats = parseExcludePlatsStr(params.get("exclude"));
			boolean commit = true;//Default: Go ahead with the commit.
			if (params.get("commit") != null && ! Boolean.valueOf(params.get("commit"))) {
				commit = false;
			}
			baProcessor.compileEnv(envId, userId, excludePlats, desc, false, commit);
			long exitCode = 0;
			Map<String,Long> result = new HashMap<String,Long>(1);
			result.put("exit_code", exitCode);
			return result;
		} catch (CmsBaseException te) {
			logger.error(te);
			te.printStackTrace();
			throw te;
		}
	}

	@RequestMapping(value="environments/{envId}/bom/generate", method = RequestMethod.GET)
	@ResponseBody
	public Map<String,Long> generateBomGet(
			@PathVariable long envId,
			@RequestHeader(value="X-Cms-User", required = false)  String userId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){
		try {
			if (userId == null) userId = "oneops-system";
			baProcessor.compileEnv(envId, userId, null, "", false, true);
			long exitCode = 0;
			Map<String,Long> result = new HashMap<String,Long>(1);
			result.put("exit_code", exitCode);
			return result;
		} catch (CmsBaseException te) {
			logger.error(te);
			te.printStackTrace();
			throw te;
		}
	}
	
	@RequestMapping(value="environments/{envId}/bom/discard", method = RequestMethod.PUT)
	@ResponseBody
	public Map<String,Long> discardBom(
			@PathVariable long envId,
			@RequestHeader(value="X-Cms-User", required = false)  String userId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){
		try {
			if (userId == null) userId = "oneops-system";

			long releaseId = envManager.discardEnvBom(envId);
			Map<String,Long> result = new HashMap<String,Long>(1);
			result.put("releaseId", releaseId);
			return result;
		} catch (CmsBaseException te) {
			logger.error(te);
			te.printStackTrace();
			throw te;
		}
	}	

	@RequestMapping(value="environments/{envId}/manifest/discard", method = RequestMethod.PUT)
	@ResponseBody
	public Map<String,Long> discardManifest(
			@PathVariable long envId,
			@RequestHeader(value="X-Cms-User", required = false)  String userId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){
		try {
			if (userId == null) {
				logger.info("userId is null, using system user for discardManifest, envId : " + envId);
				userId = ONEOPS_SYSTEM_USER;
			}

			long releaseId = envManager.discardEnvManifest(envId, userId);
			Map<String,Long> result = new HashMap<String,Long>(1);
			result.put("releaseId", releaseId);
			return result;
		} catch (CmsBaseException te) {
			logger.error(te);
			te.printStackTrace();
			throw te;
		}
	}	
	
	@RequestMapping(value="environments/{envId}/bom/discard", method = RequestMethod.GET)
	@ResponseBody
	public Map<String,Long> discardBomGet(
			@PathVariable long envId,
			@RequestHeader(value="X-Cms-User", required = false)  String userId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){
		try {
			if (userId == null) userId = "oneops-system";
			
			long releaseId = envManager.discardEnvBom(envId);
			Map<String,Long> result = new HashMap<String,Long>(1);
			result.put("releaseId", releaseId);
			return result;
		} catch (CmsBaseException te) {
			logger.error(te);
			te.printStackTrace();
			throw te;
		}

	}	
	
	
	@RequestMapping(value="environments/{envId}/unlock", method = RequestMethod.GET)
	@ResponseBody
	public Map<String,String> unlockEnv(
			@PathVariable long envId,
			@RequestHeader(value="X-Cms-User", required = false)  String userId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){
		try {
			if (userId == null) userId = "oneops-system";
	
			baProcessor.resetEnv(envId);
			
			Map<String, String> result = new HashMap<String, String>(1);
			result.put("environment state", "default");
			return result;
		} catch (CmsBaseException te) {
			logger.error(te);
			te.printStackTrace();
			throw te;
		}

	}
	
	
	@RequestMapping(value="environments/{envId}/deployments/deploy", method = RequestMethod.POST)
	@ResponseBody
	public Map<String,Long> generateAndDeployBom(
			@PathVariable long envId,
			@RequestBody Map<String,String> paramMap,
			@RequestHeader(value="X-Cms-User", required = false)  String userId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){
		try {
			if (userId == null) userId = "oneops-system";
	
			long startTime = System.currentTimeMillis(); 
	
			Map<String,Long> result = new HashMap<String,Long>(1); 
	
			//long dpmtId = bomManager.generateAndDeployBom(envId, userId, descMap.get("description"));
			Set<Long> excludePlats = parseExcludePlatsStr(paramMap.get("exclude"));
			boolean commit = true;//Default: Go ahead with the commit.
			if (paramMap.get("commit") != null && ! Boolean.valueOf(paramMap.get("commit"))) {
				commit = false;
			}
			baProcessor.compileEnv(envId, userId, excludePlats, paramMap.get("description"), true, commit);
			result.put("deploymentId", 0L);
	
			long tookTime = System.currentTimeMillis() - startTime;
			logger.debug("Time to generate Bom - " + tookTime);
			
			return result;
		} catch (CmsBaseException te) {
			logger.error(te);
			te.printStackTrace();
			throw te;
		}

	}

	
	@RequestMapping(value="environments/{envId}/reset", method = RequestMethod.GET)
	@ResponseBody
	public Map<String,Long> resetBom(
			@PathVariable long envId,
			@RequestHeader(value="X-Cms-User", required = false)  String userId){

		if (userId == null) userId = "oneops-system";

		long startTime = System.currentTimeMillis(); 

		Map<String,Long> result = new HashMap<String,Long>(1); 

		envManager.cleanEnvBom(envId);
		result.put("cleanEnvId", envId);

		long tookTime = System.currentTimeMillis() - startTime;
		logger.debug("Time to generate Bom - " + tookTime);
		
		return result;

	}

	@RequestMapping(value="environments/{envId}/cloud/{cloudId}", method = RequestMethod.GET)
	@ResponseBody
	public Map<String,String> updateCloudAdminStatus(
			@PathVariable long envId,
			@PathVariable long cloudId,
			@RequestParam(value="adminstatus", required = true) String adminstatus,
			@RequestHeader(value="X-Cms-User", required = false)  String userId){
		try {
			if (userId == null) userId = "oneops-system";
	
			Map<String,String> result = new HashMap<String,String>(1); 
	
			manifestManager.updateCloudAdminStatus(cloudId, envId, adminstatus, userId);
			result.put("result", "updated");
			return result;
		} catch (CmsBaseException te) {
			logger.error(te);
			te.printStackTrace();
			throw te;
		}
	}

	@RequestMapping(value="environments/{envId}/cloud/{cloudId}", method = RequestMethod.PUT)
	@ResponseBody
	public Map<String,String> updateCloudAdminStatus(
			@PathVariable long envId,
			@PathVariable long cloudId,
			@RequestBody Map<String,String> adminstatusMap,
			@RequestHeader(value="X-Cms-User", required = false)  String userId){
		try {
			if (userId == null) userId = "oneops-system";
	
			Map<String,String> result = new HashMap<String,String>(1); 
			String adminstatus = adminstatusMap.get("adminstatus");
			manifestManager.updateCloudAdminStatus(cloudId, envId, adminstatus, userId);
			result.put("result", "updated");
			return result;
		} catch (CmsBaseException te) {
			logger.error(te);
			te.printStackTrace();
			throw te;
		}
	
	}	
	
	@RequestMapping(value="environments/{envId}/clouds", method = RequestMethod.PUT)
	@ResponseBody
	public Map<String,Long> updateEnvClouds(
			@PathVariable long envId,
			@RequestBody CmsCIRelationSimple[] cloudRels,
			@RequestHeader(value="X-Cms-User", required = false)  String userId){
		
		try {
			if (userId == null) userId = "oneops-system";
	
			List<CmsCIRelation> rels = new ArrayList<CmsCIRelation>();
			for (CmsCIRelationSimple cloudRel : cloudRels) {
				rels.add(util.custCIRelationSimple2CIRelation(cloudRel, null));		
			}		
			long releaseId = manifestManager.updateEnvClouds(envId, rels, userId);
			Map<String,Long> result = new HashMap<String,Long>();
			result.put("releaseId", releaseId);
			return result;
		} catch (CmsBaseException te) {
			logger.error(te);
			te.printStackTrace();
			throw te;
		}

	}	
	
	@RequestMapping(value="platforms/{platId}/activate", method = RequestMethod.PUT)
	@ResponseBody
	public Map<String,Long> activatePlatform(
			@PathVariable long platId,
			@RequestHeader(value="X-Cms-User", required = false)  String userId){
		
		long releaseId = manifestManager.activatePlatform(platId, userId);

		Map<String,Long> result = new HashMap<String,Long>(1); 
		result.put("releaseId", releaseId);
		return result;
	}

	@RequestMapping(value="platforms/{platId}/clouds", method = RequestMethod.PUT)
	@ResponseBody
	public Map<String,String> updatePlatformCloud(
			@PathVariable long platId,
			@RequestBody CmsRfcRelationSimple cloudRel,
			@RequestHeader(value="X-Cms-User", required = false)  String userId){
		
		manifestManager.updatePlatformCloud(util.custRfcRelSimple2RfcRel(cloudRel), userId);

		Map<String,String> result = new HashMap<String,String>(1); 
		result.put("result", "success");
		return result;
	}
	
	
	@RequestMapping(value="platforms/{platId}/disable", method = RequestMethod.GET)
	@ResponseBody
	public Map<String,Long> disablePlatformGet(
			@PathVariable long platId,
			@RequestHeader(value="X-Cms-User", required = false)  String userId){
		
		long releaseId = manifestManager.disablePlatform(platId, userId);

		Map<String,Long> result = new HashMap<String,Long>(1); 
		result.put("releaseId", releaseId);
		return result;
	}
	
	@RequestMapping(value="platforms/{platId}/enable", method = RequestMethod.GET)
	@ResponseBody
	public Map<String,Long> enablePlatformGet(
			@PathVariable long platId,
			@RequestHeader(value="X-Cms-User", required = false)  String userId){
		
		long releaseId = manifestManager.enablePlatform(platId, userId);

		Map<String,Long> result = new HashMap<String,Long>(1); 
		result.put("releaseId", releaseId);
		return result;
	}

	@RequestMapping(value="platforms/{platId}/disable", method = RequestMethod.PUT)
	@ResponseBody
	public Map<String,Long> disablePlatform(
			@PathVariable long platId,
			@RequestHeader(value="X-Cms-User", required = false)  String userId){
		
		long releaseId = manifestManager.disablePlatform(platId, userId);

		Map<String,Long> result = new HashMap<String,Long>(1); 
		result.put("releaseId", releaseId);
		return result;
	}
	
	@RequestMapping(value="platforms/{platId}/enable", method = RequestMethod.PUT)
	@ResponseBody
	public Map<String,Long> enablePlatformPut(
			@PathVariable long platId,
			@RequestHeader(value="X-Cms-User", required = false)  String userId){
		
		long releaseId = manifestManager.enablePlatform(platId, userId);

		Map<String,Long> result = new HashMap<String,Long>(1); 
		result.put("releaseId", releaseId);
		return result;
	}
	
	
	
	@RequestMapping(method=RequestMethod.PUT, value="platforms/{platId}/iaas")
	@ResponseBody
	public Map<String,Long> upsertIaas(
			@PathVariable long platId,
			@RequestBody IaasRequest iaasRequest,
			@RequestHeader(value="X-Cms-User", required = false)  String userId) {	

		if (userId == null) userId = "oneops-system";

		long iaasId = iaasManager.processPlatformIaas(iaasRequest, platId, userId);
		Map<String,Long> result = new HashMap<String,Long>(1);
		result.put("iaasPlatId", iaasId);
		return result;
	}


    /**
     * /platforms/{platformId}/rfcs, GET
     * /platforms/{platformId}/rfcs/commit/discard PUT
     * @param platId
     * @param userId
     * @return
     */
    @RequestMapping(method=RequestMethod.GET, value="platforms/{platId}/rfcs")
    @ResponseBody
    public Map<String, List<?>> getPlatformRfcs(
            @PathVariable long platId,
            @RequestHeader(value="X-Cms-User", required = false)  String userId) {
        if (userId == null) userId = "oneops-system";
        

        return dManager.getPlatformRfcs(platId, userId);
    }

    @RequestMapping(method=RequestMethod.PUT, value="platforms/{platId}/rfcs/discard")
    @ResponseBody
    public Map<String,Long> discardPlatformRfcs(
            @PathVariable long platId,
            @RequestHeader(value="X-Cms-User", required = false)  String userId) {
        long releaseId = dManager.discardReleaseForPlatform(platId, userId);

        Map<String,Long> result = new HashMap<>(1);
        result.put("releaseId", releaseId);
        return result;
    }

    @RequestMapping(method=RequestMethod.PUT, value="platforms/{platId}/rfcs/commit")
    @ResponseBody
    public Map<String,Long> commitPlatformRfcs(
            @PathVariable long platId,
			@RequestParam(value="desc", required = false) String desc,
            @RequestHeader(value="X-Cms-User", required = false)  String userId) {
        long releaseId = dManager.commitReleaseForPlatform(platId, desc, userId);

        Map<String,Long> result = new HashMap<>(1);
        result.put("releaseId", releaseId);
        return result;
    }
    
    


    @RequestMapping(value="/flex", method = RequestMethod.GET)
	@ResponseBody
	public Long processFlex(
			@RequestParam(value="envId", required = true) long envId,
			@RequestParam(value="relId", required = true) long flexRelId,
			@RequestParam(value="step", required = true) int step,
			@RequestParam(value="scaleUp", required = true) boolean scaleUp,
			@RequestHeader(value="X-Cms-User", required = false)  String userId){
		try {
			baProcessor.processFlex(envId, flexRelId, step, scaleUp);
			//flexManager.processFlex(flexRelId, step, scaleUp, envId);
			return 0L;
		} catch (CmsBaseException te) {
			logger.error(te);
			//for whatever reason spring would not forward the exceptioon to the handler unless thrown from within controller
			throw te;
		}
	}


	@RequestMapping(value="platforms/{platId}/pack_refresh", method = RequestMethod.PUT)
	@ResponseBody
	public Map<String,Long> packSyncPlatform(
			@PathVariable long platId,
			@RequestHeader(value="X-Cms-User", required = false)  String userId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){

		if (userId == null) userId = "oneops-system";

		long releaseId = dManager.refreshPack(platId ,userId ,scope);

		Map<String,Long> result = new HashMap<String,Long>(1);
		result.put("releaseId", releaseId);
		return result;
	}
	
	
	private Set<Long> parseExcludePlatsStr(String excludePlatsStr) {
		Set<Long> excludePlats = null;
		if (excludePlatsStr != null && excludePlatsStr.length() > 0) {
			excludePlats = new HashSet<Long>();
			for (String platIdStr : excludePlatsStr.split(",")) {
				excludePlats.add(Long.valueOf(platIdStr));
			}
		}
		return excludePlats;
	}

}
