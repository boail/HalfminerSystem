package de.halfminer.hmr.interfaces;

import de.halfminer.hmr.rest.RESTCommand;
import fi.iki.elonen.NanoHTTPD;

/**
 * {@link RESTCommand REST commands's} supporting {@link NanoHTTPD.Method#POST HTTP POST}.
 * Meant to be used for data creation.
 */
public interface POSTCommand {

    NanoHTTPD.Response doOnPOST();
}