/**
 *	Copyright ? 2005 Cordys Systems B.V. All rights reserved.
 *
 *	The computer program(s) is the proprietary information of Cordys Systems B.V.,
 *	and provided under the relevant Agreement between yourself and Cordys Systems B.V.
 *	containing restrictions on use and disclosure, and are also protected by copyright,
 *	patent, and other intellectual and industrial property laws. No part of this program
 *	may be used/copied without the prior written consent of Cordys Systems B.V.
 *
 *	File Name	: coelib.js
 *  Author		: awisse
 *
 *  Goal: Provide front end developers a basic set of library functions
 */


// global object used for communicating with the backend
var __COELIB__dataObject = null;

function inXForms()
{
	return typeof( WebForm ) != "undefined" && WebForm != null;
}

/**
 * Cleanup function. Call this one on closing of your page! This is your responsability
 **/
function coelibCleanup()
{
	if( !inXForms() )
	{
		if( __COELIB__dataObject != null )
			application.removeLibrary("/cordys/wcp/library/data/busdataisland.htm", __COELIB__dataObject);
	}
	__COELIB__dataObject = null;
}





// -------------------------------  Data communication functions ----------------



/**
 *	Create a soap request with parameters
 *	@param namespace the namespace of the method
 *	@param method	the method
 *	@param parameterArray an array of parameters using the key as the XML nodename. 
 *		if a key starts with an '@' symbol it is treated as an attribute
 *		else it creates it as a child node.. If the element of the array contains another array, it will be processed as child of the current node.
 *	@return a ready to send SOAP request xml
 ***/
function createSOAPRequest( namespace, method, parameterArray )
{
	var xml = new ActiveXObject("Microsoft.XMLDOM");
	xml.documentElement = xml.createElement( "SOAP:Envelope" );
	xml.documentElement.setAttribute( "xmlns:SOAP", "http://schemas.xmlsoap.org/soap/envelope/" );
	var body = xml.createElement( "SOAP:Body" );
	xml.documentElement.appendChild( body );
	var methodNode = xml.createElement( method );
	methodNode.setAttribute( "xmlns", namespace );
	body.appendChild( methodNode );

	function handleParameters( pArr, cNode )
	{
		for( var parameter in pArr )
		{
			if( parameter.charAt( 0 ) != '@' )
			{
				var pNode = xml.createElement( parameter );
				if( pArr[ parameter ] != null )
				{
					if( typeof( pArr[ parameter ] )=="array" || typeof( pArr[ parameter ] )=="object" )
						handleParameters( pArr[ parameter ], pNode );
					else
						pNode.text = pArr[ parameter ];
				}
				cNode.appendChild( pNode );
			} else
			{
				cNode.setAttribute( parameter.substr( 1 ), pArr[ parameter ] );
			}
		}
	}

	if( parameterArray != null )
	{
		handleParameters( parameterArray, methodNode );
	}
	return xml;
}

/**
 * Returns the response xml for a given request
 * @param namespace (see createSOAPRequest)
 * @param method (see createSOAPRequest)
 * @param parameters (see createSOAPRequest)
 * @param events array containing events using XForms convention (same as xforms model events)
 * @return the data
 **/
function getDataForRequest( namespace, method, parameters, events )
{
	return executeRequest( createSOAPRequest( namespace, method, parameters ), events );
}

/**
 * Returns the response xml for a given request
 * @param request XML object containing the SOAP:Envelope to send
 * @param events array containing events using XForms convention (same as xforms model events)
 * @return the data
 **/
function executeRequest( request, events )
{
	var nrequest = request;
	if( nrequest.documentElement == null )
	{
		nrequest = new ActiveXObject( "Microsoft.XMLDOM" );
		nrequest.loadXML( request.xml );
	}

	if( inXForms() )
	{
		// we are within XForms...
		if( __COELIB__dataObject == null )
		{
			var dataInstance = WebForm.getInstance( "", "", "bla", application );
			__COELIB__dataObject = WebForm.getModel( dataInstance, "dataModel" );
		}		
		__COELIB__dataObject.setGetDatasetRequest( nrequest );
		
		if( events != null )
			__COELIB__dataObject.events = events;

		// execute request
		__COELIB__dataObject.getDataset();

		if( events != null ) // reset events:
			__COELIB__dataObject.events = new Array();

		// return result
		return __COELIB__dataObject.getData();
	} else
	{
		var data = null;
		if( __COELIB__dataObject == null )
		{
			var __COELIB__dataObject = document.createElement( "EIBUS:dataisland" );
			__COELIB__dataObject.async = false;
			__COELIB__dataObject.automatic = false;
			application.addLibrary("/cordys/wcp/library/data/busdataisland.htm", __COELIB__dataObject);
		}
		__COELIB__dataObject.request = nrequest;
		__COELIB__dataObject.clear();

		// TODO: add events here!

		__COELIB__dataObject.reset();
		
		data = __COELIB__dataObject.data.cloneNode( true );
		
		// clean up the used stuff
		__COELIB__dataObject.clear();

		return data;
	}
}

/**
 * Gets a node value for a specific xml node or the defaultValue if the node wasn't found
 * @param xml xml object
 * @param xpath the xpath to the node
 * @param defaultValue the default value
 * @param xpathIsAbsolute optional, true if don't want the catchy tuple/new, tuple old lookup feature
 * @return the node value or default value
 **/
function getNodeValue( xml, xpath, defaultValue, xpathIsAbsolute )
{
	var fromNode = xml;
	// make sure that we are selecting from a DOM node instead of a DOM document (behaves different)
	if( fromNode.documentElement != null )
		fromNode = fromNode.documentElement;
	if( !xpathIsAbsolute && fromNode.nodeName == "tuple" )
	{
		var node = fromNode.selectSingleNode( "new/*/"+xpath );
		if( node == null )
			node = fromNode.selectSingleNode( "old/*/"+xpath );

		if( node != null )
			return node.text;
	}
	var node = fromNode.selectSingleNode( xpath );
	if( node != null )
		return node.text;
	else
		return defaultValue;
}

/**
 * Removes all whitespace text nodes from the given XML.
 */
function removeXmlWhiteSpace(rootNode) {
	var children = rootNode.childNodes;
	
	if (! children) {
		return;
	}
	
	var toBeRemoved = new Array();
	
	for (var i = 0; i < children.length; i++) {
		var node = children[i];
		
		switch (node.firstChild.nodeType)
		{
		case 1 : // ELEMENT
		case 9 : // DOCUMENT
			removeXmlWhiteSpace(node);
			break;
			
		case 3 : // TEXT
		case 4 : // CDATA
		{
			// Text or CDATA.
			var sValue = node.text;
			
			if (sValue) {
				sValue = sValue.replace(/\s//g, "");
			}
			
			if (sValue.length == 0) {
				toBeRemoved.push(node);
			}
		} break;
		}
	}
	
	for (var i = 0; i < toBeRemoved.length; i++) {
		var node = toBeRemoved[i];
		
		rootNode.removeChild(node);
	}
}

/**
 * Converts a xml to transactional
 * @param sourceXML the xml to transform
 * @param splitXPath the xpath to split the sourceXML on
 * @return the transactional XML
 **/
function convertXML2TransactionalXML( sourceXML, splitXPath )
{
	var newXML = new ActiveXObject("Microsoft.XMLDOM");
	var oldDataXML = new ActiveXObject("Microsoft.XMLDOM");
	oldDataXML.loadXML(sourceXML.xml);

	// initialise XSLT:
	var cXSLT = new ActiveXObject("Microsoft.XMLDOM");
	cXSLT.loadXML( "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"1.0\"><xsl:output indent=\"yes\" method=\"xml\" version=\"4.0\"/><xsl:template match=\"/\"><dataset xmlns=\"\"><data><GetRequestResponse><xsl:for-each select=\"\"><tuple xmlns=\"\"><old><xsl:copy-of select=\".\"/></old></tuple></xsl:for-each></GetRequestResponse></data></dataset></xsl:template></xsl:stylesheet>" )
	cXSLT.selectSingleNode( "//xsl:for-each[ @select='' ]" ).setAttribute( "select", splitXPath );  
	
	// reset all the attributes on the data node
	var attrs = oldDataXML.selectNodes( "/dataset/data/*/@*" );
	var i = attrs.length;
	var getRequestResponseNode = cXSLT.selectSingleNode( "//GetRequestResponse" );
	while( i-- )
	{
		getRequestResponseNode.setAttribute( attrs[ i ].nodeName, attrs[ i ].text );
	}
	try
	{            
		oldDataXML.transformNodeToObject( cXSLT, newXML );
	} catch( e ) 
	{
		alert(  "Error found in XSL transformation: \n"
			+    "  - "+e.name+" ("+(e.number & 0xFFFF)+"): "+e.description+ "\n" );
		return;
	}         
	
	//alert(oldDataXML.xml);
	//alert(newXML.xml);
	
	cXSLT = null;
	oldDataXML = null;  
	return newXML;
}



// -------------------------------  end Data communication functions ----------------

/***
 * Fires an application.select with the given parameters
 * @param url the url to load
 * @param frame the frame to load the page into
 * @param caption the caption of the page
 * @param description the description
 * @param data the data (javascript) object to send to the new page
 * @param features the features to be used when opening in a new window instead of using the docking meganism
 * @param docked open the page docked
 * @param left left position
 * @param top top position
 * @param width the width
 * @param height the height
 * @param callBack function pointer to a function called at the moment the page is loaded
 * @param useapplication an optional application object to be used in the application.select
 **/ 
function loadPage( url, frame, id, caption, description, data, features, docked, left, top, width, height, callBack, useapplication ) 
{
	var appxml = new ActiveXObject( "Microsoft.XMLDOM" );
	appxml.loadXML( "<Application><id /><description /><caption /><url /><frame /></Application>" );
	appxml.selectSingleNode( "//id" ).text = id;
	appxml.selectSingleNode( "//description" ).text = description;
	appxml.selectSingleNode( "//caption" ).text = caption;
	appxml.selectSingleNode( "//url" ).text = url.indexOf( "/" )==-1 ? window.location.pathname.substr( 0, window.location.pathname.lastIndexOf( "/" ) ) + "/" + url : url;
	appxml.selectSingleNode( "//frame" ).text = frame;
	if( features != null )
		appxml.selectSingleNode( "//frame" ).setAttribute( "features", features );
	if( docked != null )
	{
		appxml.selectSingleNode( "//frame" ).setAttribute( "docked", docked ? "true": "false" );
		appxml.selectSingleNode( "//frame" ).setAttribute( "left", left==null ? 100 : left );
		appxml.selectSingleNode( "//frame" ).setAttribute( "top", top==null ? 100 : top );
		appxml.selectSingleNode( "//frame" ).setAttribute( "width", width==null ? 100 : width );
		appxml.selectSingleNode( "//frame" ).setAttribute( "height", height==null ? 100 : height );
	}
	if( frame == "_modal" )
	{
		appxml.selectSingleNode( "//frame" ).text = "main";
		appxml.selectSingleNode( "//frame" ).setAttribute( "docked", "false" );
		
		if( features != null && features != "" )
		{
			var attrArray = new Array();
			features.replace( /\s*(\w+)\s*:\s*([\d\w]+)\s*(?:,|;|$)/g, function( p0, p1, p2 ) { attrArray[ p1 ] = p2 } );
			if( attrArray[ "dialogLeft" ] != null )
				appxml.selectSingleNode( "//frame" ).setAttribute( "left", parseInt( attrArray[ "dialogLeft" ] ) );
			if( attrArray[ "dialogTop" ] != null )
				appxml.selectSingleNode( "//frame" ).setAttribute( "top", parseInt( attrArray[ "dialogTop" ] ) );
			if( attrArray[ "dialogWidth" ] != null )
				appxml.selectSingleNode( "//frame" ).setAttribute( "width", parseInt( attrArray[ "dialogWidth" ] ) );
			if( attrArray[ "dialogHeight" ] != null )
				appxml.selectSingleNode( "//frame" ).setAttribute( "height", parseInt( attrArray[ "dialogHeight" ] ) );
		}
	}
	if( useapplication == null )
		useapplication = application;
	useapplication.select( appxml.documentElement, data, callBack );	
}

function changeZoomWindowFeatures( applicationDefinition, newFeatureValues )
{
	var xml = applicationDefinition.documentElement ? applicationDefinition.documentElement : applicationDefinition;
	
	var frame = xml.selectSingleNode( "frame" );
	var features = new Array();
	if( frame != null && frame.getAttribute( "features" ) != null )
		features = frame.getAttribute( "features" ).split( ";" );
	
	var i = features.length;
	var fv;
	var doneFeatures = new Array();
	while( i-- )
	{
		fv = features[ i ].split( ":" );
		if( newFeatureValues[ fv[ 0 ] ] != null )
		{
			features[ i ] = fv[ 0 ] + ":" + newFeatureValues[ fv[ 0 ] ];
			doneFeatures[ fv[ 0 ] ] = true;
		}
	}
	for( var i in newFeatureValues )
	{
		if( !doneFeatures[ i ] )
			features.push( i +":"+ newFeatureValues[ i ] );
	}
	
	frame.setAttribute( "features", features.join( ";" ) );
}

/***
 * String.isOneOfThese
 *	Checks if the string is found in one of the parameters
 *  @param parameters a list of strings to search in
 *	@return boolean, true if found, false if not
 */
String.prototype.isOneOfThese = function( list ) 
{
	var value = this.toString();
	var alist = list;
	if( typeof( list ) == "string" )
		alist = arguments;
	var i = alist.length;
	while( i-- )
	{
		try 
		{
			if( alist[ i ] == value ) return true;
		} catch( e ) {};
	}
	return false;	
}

/***
 * Removes whitespaces (space and tab) in front and at the end of a string..
 * @return the trimmed string
 **/ 
String.prototype.trim = function()
{
	var str = this;

	while( str.charAt( 0 ) == " " || str.charAt( 0 ) == "\t" ) str = str.substr( 1 );

	while( str.charAt( str.length-1 ) == " " ) str = str.substr( 0, str.length - 1 );

	return str;
}


/***
 * Function to refresh all views of a model
 * Needed because the model.refreshAllViews also clears the model (from the C1 build). This one doesn't do that.
 * @param model the model to refresh the views from
 **/
function refreshModelViews( model )
{
	model.calculateTuples( false );
	for( var i in model.views )
		model.views[ i ].refreshRenderer();
}
 

/***
 * Fix to prevent nasty behaviour caused by pressing some keys at the wrong moment.
 * Keys that are fixed:
 *  - Backspace @ situations pressed outside a input or textarea, causing normally a history.back()
 *	- CTRL-W in every situation: causing a forced browser close...
 **/
if( typeof( COELIB_SKIP_KEYFIX ) == "undefined" || !COELIB_SKIP_KEYFIX )
{
	var _currentOnload = window.onload;
	window.onload = function( eventObject )
	{
		document.body.attachEvent( "onkeydown", _keyDownCheck );
		
		if( _currentOnload != null )
			_currentOnload( eventObject );
		_currentOnload = null;
		window.onload = null; // detach it to prevent memory leaks
	}
} 
function _keyDownCheck()
{
    if( window.event.keyCode == 8 )
        window.event.returnValue = document.activeElement == null ? false : document.activeElement.nodeName.toLowerCase().isOneOfThese( "input", "textarea" ) && !document.activeElement.readOnly;
    else if( window.event.keyCode == 87 && window.event.ctrlKey ) // ctrl-W, makes normally your IE go away... (no, this wasn't written around 5 december!)
    	window.event.returnValue = false;
}

/***
 * Converts an XML Definition into a html object. Usefull for XForms, you can define your definition in XML, and convert it to HTML
 * @param xml The XML defintion
 * @param append2Object Optional HTML object to append the created html object to
 * @return The created HTML Object
 **/
function convertXMLDefinitionToHTMLObject( xml, append2Object )
{
	var html = document.createElement( xml.nodeName );
	var i = xml.attributes.length;
	while( i-- )
		html.setAttribute( xml.attributes[ i ].nodeName, xml.attributes[ i ].text );

	i = xml.childNodes.length;
	while( i-- )
	{
		if( xml.childNodes[ i ].nodeName == "#text" )
		{
			html.innerText += xml.childNodes[ i ].text;
		} else
		{
			var childHTML = convertXMLDefinitionToHTMLObject( xml.childNodes[ i ] );
			html.insertAdjacentElement( "AfterBegin", childHTML );
		}
	}
	if( append2Object != null )
		append2Object.appendChild( html );
	return html;
}

/***
 * Converts an XML Definition into a Cordys html object. Usefull for XForms, you can define your definition in XML, and convert it to HTML
 * @param xml The XML defintion
 * @param attachLibrary the url to attach to your html object
 * @param append2Object Optional HTML object to append the created html object to
 * @return The created HTML Object
 **/
function convertXMLDefinitionToCordysObject( xml, attachLibrary, append2Object )
{
	var html = convertXMLDefinitionToHTMLObject( xml, append2Object );

	application.addLibrary( attachLibrary, html );
	return html;
}

/**
*
*  Base64 encode / decode
*  http://www.webtoolkit.info/
*
**/
var Base64 = {
    // private property
    _keyStr : "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=",

    // public method for encoding
    encode : function (input) {
        var output = "";
        var chr1, chr2, chr3, enc1, enc2, enc3, enc4;
        var i = 0;

        input = Base64._utf8_encode(input);

        while (i < input.length) {

            chr1 = input.charCodeAt(i++);
            chr2 = input.charCodeAt(i++);
            chr3 = input.charCodeAt(i++);

            enc1 = chr1 >> 2;
            enc2 = ((chr1 & 3) << 4) | (chr2 >> 4);
            enc3 = ((chr2 & 15) << 2) | (chr3 >> 6);
            enc4 = chr3 & 63;

            if (isNaN(chr2)) {
                enc3 = enc4 = 64;
            } else if (isNaN(chr3)) {
                enc4 = 64;
            }

            output = output +
            this._keyStr.charAt(enc1) + this._keyStr.charAt(enc2) +
            this._keyStr.charAt(enc3) + this._keyStr.charAt(enc4);

        }

        return output;
    },

    // public method for decoding
    decode : function (input) {
        var output = "";
        var chr1, chr2, chr3;
        var enc1, enc2, enc3, enc4;
        var i = 0;

        input = input.replace(/[^A-Za-z0-9\+\/\=]/g, "");

        while (i < input.length) {

            enc1 = this._keyStr.indexOf(input.charAt(i++));
            enc2 = this._keyStr.indexOf(input.charAt(i++));
            enc3 = this._keyStr.indexOf(input.charAt(i++));
            enc4 = this._keyStr.indexOf(input.charAt(i++));

            chr1 = (enc1 << 2) | (enc2 >> 4);
            chr2 = ((enc2 & 15) << 4) | (enc3 >> 2);
            chr3 = ((enc3 & 3) << 6) | enc4;

            output = output + String.fromCharCode(chr1);

            if (enc3 != 64) {
                output = output + String.fromCharCode(chr2);
            }
            if (enc4 != 64) {
                output = output + String.fromCharCode(chr3);
            }

        }

        output = Base64._utf8_decode(output);

        return output;

    },

    // private method for UTF-8 encoding
    _utf8_encode : function (string) {
        string = string.replace(/\r\n/g,"\n");
        var utftext = "";

        for (var n = 0; n < string.length; n++) {

            var c = string.charCodeAt(n);

            if (c < 128) {
                utftext += String.fromCharCode(c);
            }
            else if((c > 127) && (c < 2048)) {
                utftext += String.fromCharCode((c >> 6) | 192);
                utftext += String.fromCharCode((c & 63) | 128);
            }
            else {
                utftext += String.fromCharCode((c >> 12) | 224);
                utftext += String.fromCharCode(((c >> 6) & 63) | 128);
                utftext += String.fromCharCode((c & 63) | 128);
            }

        }

        return utftext;
    },

    // private method for UTF-8 decoding
    _utf8_decode : function (utftext) {
        var string = "";
        var i = 0;
        var c = c1 = c2 = 0;

        while ( i < utftext.length ) {

            c = utftext.charCodeAt(i);

            if (c < 128) {
                string += String.fromCharCode(c);
                i++;
            }
            else if((c > 191) && (c < 224)) {
                c2 = utftext.charCodeAt(i+1);
                string += String.fromCharCode(((c & 31) << 6) | (c2 & 63));
                i += 2;
            }
            else {
                c2 = utftext.charCodeAt(i+1);
                c3 = utftext.charCodeAt(i+2);
                string += String.fromCharCode(((c & 15) << 12) | ((c2 & 63) << 6) | (c3 & 63));
                i += 3;
            }

        }

        return string;
    }
}

/**
 * Dump object properties
 */
var DumpObject = {
	show_props_R : function(obj, obj_name, equ, delim, depth) {
	  var result = '';
	  
      if (typeof(obj) == "undefined") {
	  	return result;
	  }	  
	  
	  depth--;
	  for (var i in obj) {
        var typ = typeof (obj[i]);
        
        if (typeof(typ) == "undefined") {
			continue;
		}
	    
//	    if (typ == "number" && i == "length") {
//	    	continue;
//	    }
	    
	    var del = (typ=="string") ? '"' : '';
	    var suf = (typ != "string" && typ != "number" && typ != "object") ? ' (' + typ + ')' : '';
	    
	    try {
		    result += obj_name+'.'+i+equ+del+obj[i]+del+suf+delim;
		}
		catch (ignored) {
			continue;
		}
	    
	    if (depth > 0) {
	      result += this.show_props_R(obj[i], obj_name+'.'+i, equ, delim, depth);
	    }
	  }
	  return result;
	},

	toString : function(object, depth, objName) {
		if (typeof(object) == "undefined") {
			return "undefined";
		}
		
		if (! depth) {
			depth = 1;
		}
		
		if (! objName) {
			objName = "object";
		}
	
  		return this.show_props_R(object, objName, " = ", "\r\n", depth);
	},
	
	alert : function(object, depth, objName) {
		alert(this.toString(object, depth, objName));
	}
}

/**
 * Sets up password field for handling encoding and decoding the data.
 */
function __onPasswordFieldDataBind(eventObject) {
	if (! eventObject.dataNode) {
		return;
	}

	var sEncodedText = eventObject.dataNode.text;
	var sPlainText = Base64.decode(sEncodedText);
	
	eventObject.dataNode.text = sPlainText;
}

function __onPasswordFieldDataChange(eventObject) {
	var sPlainText = eventObject.srcElement.getValue();
	var sEncodedText = Base64.encode(sPlainText);

	if (eventObject.srcElement.data) {
		eventObject.srcElement.data.text = sEncodedText;
	}
}

function setupPasswordField(field) {
	field.xforms_before_data_bind = "__onPasswordFieldDataBind";
	field.xforms_value_changed = "__onPasswordFieldDataChange";
}

