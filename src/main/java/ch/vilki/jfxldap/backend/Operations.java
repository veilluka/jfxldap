package ch.vilki.jfxldap.backend;


import com.unboundid.ldap.sdk.*;
import com.unboundid.ldif.LDIFException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;

public class Operations {

    static Logger logger = LogManager.getLogger(Operations.class);

    // this method is actually slow, o(n)^2. could be made o(n)
    public static void deleteWithChildren(LDAPConnection connection, String dn) throws LDAPException {
       try {
           SearchResult result = null;
           try
           {
               result = connection.search(dn,SearchScope.SUB, Filter.createPresenceFilter("objectClass"));
           }
           catch (Exception e)
           {
               logger.debug("Can not delete entry as it is not found->" + dn);
           }

            if (result == null || result.getSearchEntries() == null || result.getSearchEntries().isEmpty())
            {
                logger.info("Workflow instance does not exist in target enviroment, need not to delete->" + dn);
            }
            while(result != null && result.getEntryCount() != 0)
            {
                for(SearchResultEntry s1: result.getSearchEntries())
                {
                    if (!hasChildren(s1.getDN(),connection))
                    {
                        connection.delete(s1.getDN());
                    }
                }
                result = null;
                try { result = connection.search(dn,SearchScope.SUB, Filter.createPresenceFilter("objectClass")); }
                catch (Exception e ){}
            }

        } catch (LDAPSearchException e) {
            e.printStackTrace();
        }
    }

    public static boolean hasChildren(String dn, LDAPConnection connection) throws LDAPSearchException {
        SearchResult result = connection.search(dn,SearchScope.ONE, Filter.createPresenceFilter("objectClass"));
        if (result == null || result.getSearchEntries() == null ||result.getSearchEntries().size() == 0) return false;
        return true;
    }

    public static List<SearchResultEntry>  search(Filter filter, String globalScope , SearchScope scope, String[] attributes, Connection connection ) throws LDAPException, IOException, LDIFException {
        SearchRequest searchRequest = null;
        List<SearchResultEntry> resultEntries = null;
        String[] searchAttributes = null;

        if(attributes != null && attributes.length > 0)
        {
            searchAttributes = attributes;
        }
        else{
            searchAttributes = UnboundidLdapSearch.getReadAttributes(UnboundidLdapSearch.READ_ATTRIBUTES.all);
        }
        SearchResult searchResult = null;
        try
        {
            searchRequest =   new SearchRequest(globalScope, scope, filter,searchAttributes);
            searchRequest.setDerefPolicy(DereferencePolicy.SEARCHING);
            resultEntries = connection.searchEntries(searchRequest);
        }
        catch (LDAPSearchException lse)
        {
            // The search failed for some reason.
            searchResult = lse.getSearchResult();
            ResultCode resultCode = lse.getResultCode();
            String errorMessageFromServer = lse.getDiagnosticMessage();
            logger.error("Search failed with following reason->"+errorMessageFromServer,lse);
            throw new LDAPSearchException(lse);
        }
        catch (Exception e)
        {
            logger.error("Search failed with following reason->",e);
        }
        return resultEntries;
    }


}
