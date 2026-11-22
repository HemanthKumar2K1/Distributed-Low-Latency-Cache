/**
 * What an application uses to talk to the cache. RULE: plain Java only, no Spring.
 * The cache itself runs in its own process (cache-system); remote/ talks to it over TCP.
 */
package com.hemz.distributedcache.modules.cacheclient;
