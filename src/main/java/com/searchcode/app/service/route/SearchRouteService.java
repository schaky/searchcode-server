/*
 * Copyright (c) 2016 Boyter Online Services
 *
 * Use of this software is governed by the Fair Source License included
 * in the LICENSE.TXT file, but will be eventually open under GNU General Public License Version 3
 * see the README.md for when this clause will take effect
 *
 * Version 1.3.15
 */

package com.searchcode.app.service.route;

import com.searchcode.app.config.Values;
import com.searchcode.app.dto.SearchResult;
import com.searchcode.app.dto.api.legacy.*;
import com.searchcode.app.service.Singleton;
import spark.Request;
import spark.Response;

import java.util.ArrayList;
import java.util.HashMap;

public class SearchRouteService {

    public SearchResult codeSearch(Request request, Response response) {
        return this.getSearchResult(request, false, true);
    }

    public SearchResult literalCodeSearch(Request request, Response response) {
        return this.getSearchResult(request, true, true);
    }

    /**
     * Legacy mapping to expose searchcode API for other clients
     * this is very ugly but should never need to be touched again
     */
    public codesearch_I codeSearch_I(Request request, Response response) {
        // TODO need to be able to turn off the escaping
        var results = this.getSearchResult(request, false, false);

        var res = new codesearch_I();

        res.page = results.getPage();
        res.matchterm = results.getQuery();
        res.query = results.getQuery();
        res.searchterm = results.getQuery();
        res.total = results.getTotalHits();

        if (res.page >= 0) {
            res.nextpage = res.page + 1;
        }

        if (res.page >= 1) {
            res.previouspage = res.page - 1;
        }

        var code = new ArrayList<result_codesearch_I>();
        for (var r : results.getCodeResultList()) {
            var t = new result_codesearch_I();
            t.id = r.getDocumentId();
            t.filename = r.fileName;
            t.linescount = Singleton.getHelpers().tryParseInt(r.lines, "0");
            t.language = r.languageName;
            t.location = r.fileLocation;
            t.md5hash = r.md5hash;
            t.name = r.repoName;
            t.repo = r.repo;
            t.url = "https://searchcode.com/codesearch/view/" + r.getDocumentId() + "/";

            t.lines = new HashMap<Integer, String>();
            for (var x : r.matchingResults) {
                t.lines.put(x.lineNumber, x.line);
            }

            code.add(t);
        }
        res.results = code;

        // Sort out the language filters
        var lang = new ArrayList<language_filter>();
        for (var r : results.getLanguageFacetResults()) {
            var t = new language_filter();
            t.count = r.count;
            t.id = r.languageId;
            t.language = r.languageName;
            lang.add(t);
        }
        res.language_filters = lang;

        // Source Filters
        var src = new ArrayList<source_filter>();
        for (var r : results.getCodeFacetSources()) {
            var t = new source_filter();
            t.count = r.count;
            t.source = r.source;
            src.add(t);
        }
        res.source_filters = src;

        return res;
    }

    // Legacy mapping to expose searchcode API for other clients
    public coderesult codeResult(Request request, Response response) {
        var codeId = Singleton.getHelpers().tryParseInt(request.params(":codeid"), "-1");

        var result = Singleton.getSourceCode().getById(codeId);

        var resp = new coderesult();
        result.ifPresent(x -> resp.code = x.content);

        return resp;
    }

    private SearchResult getSearchResult(Request request, boolean isLiteral, boolean highlight) {
        if (!request.queryParams().contains("q") || request.queryParams("q").trim().equals(Values.EMPTYSTRING)) {
            return null;
        }

        var query = request.queryParams("q").trim();
        var page = 0;
        page = CodeRouteService.getPage(request, page);

        var facets = new HashMap<String, String[]>();

        if (request.queryParams().contains("repo")) {
            facets.put("repo", request.queryParamsValues("repo"));
        }
        if (request.queryParams().contains("lan")) {
            facets.put("lan", request.queryParamsValues("lan"));
        }
        if (request.queryParams().contains("own")) {
            facets.put("own", request.queryParamsValues("own"));
        }
        if (request.queryParams().contains("fl")) {
            facets.put("fl", new String[]{request.queryParams("fl")});
        }
        if (request.queryParams().contains("src")) {
            facets.put("src", request.queryParamsValues("src"));
        }

        if (query.trim().startsWith("/") && query.trim().endsWith("/")) {
            isLiteral = true;
        }

        var searchResult = Singleton.getIndexService().search(query, facets, page, isLiteral);

        searchResult.setCodeResultList(Singleton.getCodeMatcher().formatResults(searchResult.getCodeResultList(), query, highlight));
        searchResult.setQuery(query);

        for (var altQuery : Singleton.getSearchCodeLib().generateAltQueries(query)) {
            searchResult.addAltQuery(altQuery);
        }

        // Null out code as it isn't required and there is no point in bloating our ajax requests
        searchResult.getCodeResultList().forEach(x -> x.setCode(null));

        return searchResult;
    }
}
