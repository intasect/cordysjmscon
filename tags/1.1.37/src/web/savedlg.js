/**
 * Sets the XMLStore GetCollection folder key for getting the proper definition XML's.
 * @param request SOAP request XML that will be modified.
 * @param key Key to be set in the SOAP request.
 */
function setXmlStoreModelRequestKey(request, key) {
    var folderNode = request.selectSingleNode(".//folder");
    
    if (folderNode) {
        folderNode.text = key;
        folderNode.setAttribute("recursive", "false");
        folderNode.setAttribute("detail", "false");
    }
}

/**
 * Adds a tuple to the XMLStore GetCollection response that contains the name '..'
 * allowing navigation to the parent folder.
 * @param nResponse GetCollectionResponse XML.
 * @param sRequestFolder Folder key of the GetCollection request.
 */
function addParentFolderTupleToXmlStoreResponse(nResponse, sRequestFolder) {
	if (! sRequestFolder || sRequestFolder == "/") {
		// Root folder does not have a parent folder.
		return;
	}
	
	var iPos = sRequestFolder.lastIndexOf("/");
	var sParentKey = iPos > 0 ? sRequestFolder.substring(0, iPos) : "/";
	
	if (! sParentKey) {
		// Invalid key.
		return;
	}	
	
	var nResponseMethod = nResponse.selectSingleNode("//GetCollectionResponse");
	
	if (! nResponseMethod) {
		return;
	}
	
	var nParentTuple = nResponseMethod.ownerDocument.createElement("tuple");
	
	nParentTuple.setAttribute("key", sParentKey);
	nParentTuple.setAttribute("name", "..");
	nParentTuple.setAttribute("isFolder", "true");	
	
	if (nResponseMethod.childNodes.length > 0) {
		nResponseMethod.insertBefore(nParentTuple, nResponseMethod.childNodes.item(0));
	} else {
		nResponseMethod.appendChild(nParentTuple);
	}
}

/**
 * Returns an object that contains selection information 
 * from a table that is bound to a model that gets
 * its data with XMLStore GetCollection  method.
 * Object contains the following fields:
 * - key - (String) key.
 * - isFolder - (Boolean) true if a folder is selected.
 * - name - (String) entry name.
 * - data (XML) entry data element.
 * @param model 
 * @return An object containing selection information or null if the selection was not set.
 */
function getXmlStoreTableSelection(model, eventObject, getModel, dataXPath) {
	if (eventObject && eventObject.selectType == "default") {
		// This comes from initialization.
		return null;
	}

	var bo = model.getActiveBusinessObject();
	
	if (! bo) {
		return null;
	}

    var nTuple = bo.getCurrent();

    if (! nTuple) {
        return null;
    }

    //alert(nTuple.xml);
    
    var sKey = nTuple.getAttribute("key");
    var sFolder = nTuple.getAttribute("isFolder");
    var sName = nTuple.getAttribute("name");
        
    if (sKey == null) {
    	alert("Key not found from the tuple.");
    	return null;
    }
    
	// Read the data from XML store.
	var nReadData = null;
	
	if (getModel) {
		getModel.getMethodRequest("Get").selectSingleNode("//key").text = sKey;
		getModel.reset();

		nReadData = getModel.getData().selectSingleNode("//tuple/old/" + dataXPath);

		if (! nReadData) {
			alert("Selected object does not contain valid data.");
			return null;
		}
	}
    
    //alert(sKey + " - " + sFolder);

    var oRes = new Object();
    
    oRes.key = sKey;
    oRes.isFolder = (sFolder == "true");
    oRes.name = sName;
    oRes.data = nReadData;
    
    return oRes;
}

function saveXmlStoreModelData(dataModel, updateModel, sDataKey, nDataXml) {
	if (! dataModel.getData()) {
		alert("Model contains no data.");
		return false;
	}
	
	var iPos = sDataKey.lastIndexOf("/");
	var sDataName = iPos > 0 ? sDataKey.substring(iPos + 1) : null;
	
	if (! sDataName) {
		alert("Unable to extract the name from the key.");
		return false;
	}	
	
	if (! updateModel.getMethodRequest("Get")) {
		alert("Get request not set in the update model.");
		return false;
	}

	var nUpdateNew = updateModel.getMethodRequest("Get").selectSingleNode("//tuple/new");
	
	if (! nUpdateNew) {
		alert("Tuple new node not found from the update model.");
		return false;
	}

	var nUpdateTuple = nUpdateNew.parentNode;	
	var nOldTuple = dataModel.getData().selectSingleNode("//tuple[@key='" + sDataKey + "']");
	var sOldLastModified = "";
	var sOldVersion;
	
	if (nOldTuple) {
		sOldLastModified = nOldTuple.getAttribute("lastModified");
		sOldVersion = nOldTuple.getAttribute("level");

		if (! sOldLastModified) {
			alert("Last modified attribute not found from the old tuple.");
			return false;
		}
	
		if (! sOldVersion) {
			alert("Version attribute not found from the old tuple.");
			return false;
		}
	
		if (sOldVersion == "organization" && 
			! confirm("Do you want to overwrite the existing file?")) {
			return false;
		}
	}
	
	nUpdateTuple.setAttribute("key", sDataKey);
	nUpdateTuple.setAttribute("name", sDataName);	
	nUpdateTuple.setAttribute("version", "organization");

	if (sOldLastModified) {
		nUpdateTuple.setAttribute("lastModified", sOldLastModified);
	} else {
		nUpdateTuple.removeAttribute("lastModified");
	}

	// Remove any old data from the request.
	while (nUpdateNew.childNodes.length > 0) {
		nUpdateTuple.removeChild(nUpdateNew.childNodes.item(0));
	}
	
	nUpdateNew.appendChild(nDataXml.cloneNode(true));
	
	//alert(updateModel.getMethodRequest("Get").xml);
	updateModel.reset();
	
	return true;
}

/**
 * Sorts the XMLStore GetCollection method response so,
 * that folders are placed first in a sorted order and files after them
 * also in sorted order.
 * @param SOAP response XML.<b> 
*/
function sortGetCollectionResponse(responseXml) {
	//alert(responseXml.xml);
	
	var responseMethodNode = responseXml.selectSingleNode("/*/*[1]/*");
	
	if (! responseMethodNode) {
		alert("Response method node not found.");
		return;
	}
	
	var folderNodeArray = new Array();
	var fileNodeArray = new Array();
	
	while (responseMethodNode.childNodes.length > 0) {
		var t = responseMethodNode.childNodes[0];

		t = t.parentNode.removeChild(t);
		
		if (t.getAttribute("isFolder") == "true") {
			folderNodeArray.push(t); 
		} else {
			fileNodeArray.push(t);
		}	
	}
	
	// Sort the arrays.
	folderNodeArray.sort(sortNodes);
	fileNodeArray.sort(sortNodes);
		
	// Create new XML from the two sorted arrays.
	var sepNode;

	for (var i = 0; i < folderNodeArray.length; i++) {
		responseMethodNode.appendChild(folderNodeArray[i]);
	}

	for (var i = 0; i < fileNodeArray.length; i++) {
		responseMethodNode.appendChild(fileNodeArray[i]);
	}
	
	//alert(responseXml.xml);
}

function sortNodes(a, b) {
	//alert("a="+a.xml + "\r\nb=" + b.xml);

	var va, vb;
	
	if (! a || ! (va = a.getAttribute("name"))) {
		return -1;
	}

	if (! b || ! (vb = b.getAttribute("name"))) {
		return 1;
	}
	
	va = va.toLowerCase();
	vb = vb.toLowerCase();
	//alert("vat: " + va + ", vbt: " + vb + ",res: " + (va < vb ? -1 : (va == vb ? 0 : 1)));	

	return va < vb ? -1 : (va == vb ? 0 : 1);
}


