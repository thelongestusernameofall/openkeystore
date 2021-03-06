/*
 *  Copyright 2006-2015 WebPKI.org (http://webpki.org).
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.webpki.keygen2;

import java.io.IOException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;

import org.webpki.crypto.AsymSignatureAlgorithms;
import org.webpki.crypto.CertificateFilter;
import org.webpki.crypto.HashAlgorithms;
import org.webpki.json.JSONObjectReader;
import org.webpki.json.JSONSignatureDecoder;
import org.webpki.sks.AppUsage;
import org.webpki.sks.Grouping;
import org.webpki.util.ArrayUtil;

import static org.webpki.keygen2.KeyGen2Constants.*;

public class CredentialDiscoveryRequestDecoder extends ClientDecoder
  {
    private static final long serialVersionUID = 1L;

    public class LookupSpecifier extends CertificateFilter
      {
        String id;
        
        GregorianCalendar issued_before;
        GregorianCalendar issued_after;
        Grouping grouping;
        AppUsage app_usage;

        PublicKey key_management_key;

        LookupSpecifier (JSONObjectReader rd) throws IOException
          {
            id = KeyGen2Validator.getID (rd, ID_JSON);
            if (!ArrayUtil.compare (nonce_reference, rd.getBinary (NONCE_JSON)))
              {
                throw new IOException ("\"" + NONCE_JSON + "\"  error");
              }
            if (rd.hasProperty (SEARCH_FILTER_JSON))
              {
                JSONObjectReader search = rd.getObject (SEARCH_FILTER_JSON);
                if (search.getProperties ().length == 0)
                  {
                    throw new IOException ("Empty \"" + SEARCH_FILTER_JSON + "\" not allowed");
                  }
                setFingerPrint (search.getBinaryConditional (CertificateFilter.CF_FINGER_PRINT));
                setIssuerRegEx (search.getStringConditional (CertificateFilter.CF_ISSUER_REG_EX));
                setSerialNumber (KeyGen2Validator.getBigIntegerConditional (search, CertificateFilter.CF_SERIAL_NUMBER));
                setSubjectRegEx (search.getStringConditional (CertificateFilter.CF_SUBJECT_REG_EX));
                setEmailRegEx (search.getStringConditional (CertificateFilter.CF_EMAIL_REG_EX));
                setPolicyRules (search.getStringArrayConditional (CertificateFilter.CF_POLICY_RULES));
                setKeyUsageRules (search.getStringArrayConditional (CertificateFilter.CF_KEY_USAGE_RULES));
                setExtendedKeyUsageRules (search.getStringArrayConditional (CertificateFilter.CF_EXT_KEY_USAGE_RULES));
                issued_before = KeyGen2Validator.getDateTimeConditional (search, ISSUED_BEFORE_JSON);
                issued_after = KeyGen2Validator.getDateTimeConditional (search, ISSUED_AFTER_JSON);
                if (search.hasProperty (GROUPING_JSON))
                  {
                    grouping = Grouping.getGroupingFromString (search.getString (GROUPING_JSON));
                  }
                if (search.hasProperty (APP_USAGE_JSON))
                  {
                    app_usage = AppUsage.getAppUsageFromString (search.getString (APP_USAGE_JSON));
                  }
              }
            JSONSignatureDecoder signature = rd.getSignature ();
            key_management_key = signature.getPublicKey ();
            if (((AsymSignatureAlgorithms) signature.getAlgorithm ()).getDigestAlgorithm () != HashAlgorithms.SHA256)
              {
                throw new IOException ("Lookup signature must use SHA256");
              }
          }


        public String getID ()
          {
            return id;
          }
        
        public PublicKey getKeyManagementKey ()
          {
            return key_management_key;
          }
        
        public GregorianCalendar getIssuedBefore ()
          {
            return issued_before;
          }

        public GregorianCalendar getIssuedAfter ()
          {
            return issued_after;
          }

        public Grouping getGrouping ()
          {
            return grouping;
          }

        public AppUsage getAppUsage ()
          {
            return app_usage;
          }

        @Override
        public boolean matches (X509Certificate[] certificate_path) throws IOException
          {
            if (issued_before != null && issued_before.getTimeInMillis () < (certificate_path[0].getNotBefore ().getTime ()))
              {
                return false;
              }
            if (issued_after != null && issued_after.getTimeInMillis () > (certificate_path[0].getNotBefore ().getTime ()))
              {
                return false;
              }
            return super.matches (certificate_path);
          }
      }

    LinkedHashMap<String,LookupSpecifier> lookup_specifiers = new LinkedHashMap<String,LookupSpecifier> ();
    
    String client_session_id;

    String server_session_id;

    String submit_url;

    byte[] nonce_reference;

    public String getServerSessionId ()
      {
        return server_session_id;
      }


    public String getClientSessionId ()
      {
        return client_session_id;
      }


    public String getSubmitUrl ()
      {
        return submit_url;
      }


    public LookupSpecifier[] getLookupSpecifiers ()
      {
        return lookup_specifiers.values ().toArray (new LookupSpecifier[0]);
      }
    
    
    @Override
    void readServerRequest (JSONObjectReader rd) throws IOException
      {
        /////////////////////////////////////////////////////////////////////////////////////////
        // Session properties
        /////////////////////////////////////////////////////////////////////////////////////////
        server_session_id = getID (rd, SERVER_SESSION_ID_JSON);

        client_session_id = getID (rd, CLIENT_SESSION_ID_JSON);

        submit_url = getURL (rd, SUBMIT_URL_JSON);
        
        /////////////////////////////////////////////////////////////////////////////////////////
        // Calculate proper nonce
        /////////////////////////////////////////////////////////////////////////////////////////
        MacGenerator mac = new MacGenerator ();
        mac.addString (client_session_id);
        mac.addString (server_session_id);
        nonce_reference = HashAlgorithms.SHA256.digest (mac.getResult ());

        /////////////////////////////////////////////////////////////////////////////////////////
        // Get the lookup specifiers [1..n]
        /////////////////////////////////////////////////////////////////////////////////////////
        for (JSONObjectReader spec : getObjectArray (rd, LOOKUP_SPECIFIERS_JSON))
          {
            LookupSpecifier ls = new LookupSpecifier (spec);
            if (lookup_specifiers.put (ls.id, ls) != null)
              {
                throw new IOException ("Duplicate id: " + ls.id);
              }
          }
      }

    @Override
    public String getQualifier ()
      {
        return KeyGen2Messages.CREDENTIAL_DISCOVERY_REQUEST.getName ();
      }
  }
