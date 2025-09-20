package com.integreat.integreatme.controller;

import java.util.List;

import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.integreat.integreatme.client.OAuth2PlatformClientFactory;
import com.intuit.ipp.core.Context;
import com.intuit.ipp.core.ServiceType;
import com.intuit.ipp.data.CompanyInfo;
import com.intuit.ipp.data.Error;
import com.intuit.ipp.exception.FMSException;
import com.intuit.ipp.security.OAuth2Authorizer;
import com.intuit.ipp.services.DataService;
import com.intuit.ipp.services.QueryResult;
import com.intuit.ipp.util.Config;

@RestController
public class QBOController {
	
	private final OAuth2PlatformClientFactory factory;
	private static final Logger logger = LoggerFactory.getLogger(QBOController.class);

    public QBOController(OAuth2PlatformClientFactory factory) {
        this.factory = factory;
    }

    /**
     * Sample QBO API call using OAuth2 tokens
     * QBOTokenFilter ensures valid access tokens are available
     * 
     * @param session
     * @return
     */
	@ResponseBody
    @RequestMapping("/getCompanyInfo")
    public String callQBOCompanyInfo(HttpSession session) {

    	String realmId = (String)session.getAttribute("realmId");
    	if (StringUtils.isEmpty(realmId)) {
    		return new JSONObject().put("response","No realm ID. QBO calls only work if the accounting scope was passed!").toString();
    	}
    	
    	// QBOTokenFilter guarantees this will be valid
    	String accessToken = (String)session.getAttribute("access_token");
    	String failureMsg = "Failed";
    	String url = factory.getPropertyValue("IntuitAccountingAPIHost") + "/v3/company";
    	
        try {
        	// set custom config
        	Config.setProperty(Config.BASE_URL_QBO, url);

    		// get DataService
    		DataService service = getDataService(realmId, accessToken);
			
			// get all companyinfo
			String sql = "select * from companyinfo";
			QueryResult queryResult = service.executeQuery(sql);
			return processResponse(failureMsg, queryResult);
			
		} catch (FMSException e) {
			List<Error> list = e.getErrorList();
			list.forEach(error -> logger.error("Error while calling executeQuery :: " + error.getMessage()));
			return new JSONObject().put("response", failureMsg).toString();
		}
    }

	private String processResponse(String failureMsg, QueryResult queryResult) {
		if (!queryResult.getEntities().isEmpty() && queryResult.getEntities().size() > 0) {
			CompanyInfo companyInfo = (CompanyInfo) queryResult.getEntities().get(0);
			logger.info("Companyinfo -> CompanyName: " + companyInfo.getCompanyName());
			ObjectMapper mapper = new ObjectMapper();
			try {
				String jsonInString = mapper.writeValueAsString(companyInfo);
				return jsonInString;
			} catch (JsonProcessingException e) {
				logger.error("Exception while getting company info ", e);
				return new JSONObject().put("response", failureMsg).toString();
			}
		}
		return failureMsg;
	}

	private DataService getDataService(String realmId, String accessToken) throws FMSException {
		// create oauth object
		OAuth2Authorizer oauth = new OAuth2Authorizer(accessToken);
		// create context
		Context context = new Context(oauth, ServiceType.QBO, realmId);
		// create dataservice
		return new DataService(context);
	}
}
