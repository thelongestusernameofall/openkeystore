/*
 *  Copyright 2006-2014 WebPKI.org (http://webpki.org).
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
package org.webpki.webapps.json.jcs;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.webpki.crypto.CustomCryptoProvider;
import org.webpki.crypto.KeyStoreReader;
import org.webpki.util.ArrayUtil;
import org.webpki.webutil.InitPropertyReader;

public class JCSService extends InitPropertyReader implements ServletContextListener
  {
    static Logger logger = Logger.getLogger (JCSService.class.getName ());
    
    static String key_password;
    
    static KeyStore clientkey_rsa;

    static KeyStore clientkey_ec;
    
    static String logotype;

    InputStream getResource(String name) throws IOException {
      InputStream is = this.getClass().getResourceAsStream(name);
      if (is == null) {
          throw new IOException("Resource fail for: " + name);
      }
      return is;
    }

    @Override
    public void contextDestroyed (ServletContextEvent event)
      {
      }

    @Override
    public void contextInitialized (ServletContextEvent event)
      {
        initProperties (event);
        try
          {
            ////////////////////////////////////////////////////////////////////////////////////////////
            // Logotype
            ////////////////////////////////////////////////////////////////////////////////////////////
            logotype = new String(
                ArrayUtil.getByteArrayFromInputStream(getResource("webpki-logo.svg")), "UTF-8");

            ////////////////////////////////////////////////////////////////////////////////////////////
            // Keys
            ////////////////////////////////////////////////////////////////////////////////////////////
            CustomCryptoProvider.forcedLoad (getPropertyBoolean ("bouncycastle_first"));
            key_password = getPropertyString ("key_password");
            clientkey_rsa = KeyStoreReader.loadKeyStore (getResource (getPropertyString ("clientkey_rsa")), key_password);
            clientkey_ec = KeyStoreReader.loadKeyStore (getResource (getPropertyString ("clientkey_ec")), key_password);
            logger.info ("JCS Demo Successfully Initiated");
          }
        catch (Exception e)
          {
            logger.log (Level.SEVERE, "********\n" + e.getMessage () + "\n********", e);
          }
      }
  }
