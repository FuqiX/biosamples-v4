package uk.ac.ebi.biosamples.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.expression.ParseException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import uk.ac.ebi.biosamples.model.*;
import uk.ac.ebi.biosamples.model.filter.Filter;
import uk.ac.ebi.biosamples.service.SamplePageService;
import uk.ac.ebi.biosamples.service.SampleService;

import javax.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;

@Controller
@RequestMapping("/sitemap")
public class SitemapController {

    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Value("${model.page.size:10000}")
    private int sitemapPageSize;

    private SampleService sampleService;
    private SamplePageService samplePageService;

    public SitemapController(SampleService service, SamplePageService pageService) {
        this.sampleService = service;
        this.samplePageService = pageService;
    }

    /**
     * Generate the sitemap index
     * @param request the request
     * @return the sitemap index in xml format
     * @throws MalformedURLException
     */
    @RequestMapping(method= RequestMethod.GET, produces= MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public XmlSitemapIndex createSampleSitemapIndex(HttpServletRequest request) throws MalformedURLException {

        long sampleCount = getTotalSamples();
        long pageNumber = (sampleCount / (long)sitemapPageSize) + 1L;
        XmlSitemapIndex xmlSitemapIndex = new XmlSitemapIndex();
        for (int i=0; i< pageNumber; i++) {
            String location = generateBaseUrl(request) + String.format("/sitemap/%d", i+1);
            XmlSitemap xmlSiteMap = new XmlSitemap(location);
            xmlSitemapIndex.addSitemap(xmlSiteMap);
        }
        return xmlSitemapIndex;

    }

    /**
     * Generate a sitemap subpage
     * @param pageNumber the page number
     * @param request the request object
     * @return the sitemap page content
     * @throws ParseException
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public XmlUrlSet createSampleSitemapPage(@PathVariable("id") int pageNumber, HttpServletRequest request) throws ParseException {
        final long startTime = System.currentTimeMillis();
        Pageable pageRequest = new PageRequest(pageNumber - 1, sitemapPageSize);
        Page<Sample> samplePage = samplePageService.getSamplesByText("", Collections.emptyList(), Collections.emptyList(),  pageRequest, null);
        XmlUrlSet xmlUrlSet = new XmlUrlSet();
        for(Sample sample: samplePage.getContent()) {
            String location = generateBaseUrl(request) + String.format("/samples/%s", sample.getAccession());
            
            LocalDate lastModifiedDate = LocalDateTime.ofInstant(sample.getUpdate(), ZoneOffset.UTC).toLocalDate();
            
            XmlUrl url = new XmlUrl.XmlUrlBuilder(location)
                    .lastModified(lastModifiedDate)
                    .hasPriority(XmlUrl.Priority.MEDIUM).build();
            xmlUrlSet.addUrl(url);
        }
        log.debug(String.format("Returning model for %d samples took %d millis", sitemapPageSize, System.currentTimeMillis() - startTime));
        return xmlUrlSet;
    }

    /**
     * Generate the proper url based on the request
     * @param request
     * @return the base url for the links in the sitemap
     */
    private String generateBaseUrl(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        String requestURL = request.getRequestURL().toString();
        return requestURL.replaceFirst(requestURI, "") +
                request.getContextPath();

    }

    /**
     * Get the total number of samples in the database
     * @return the number of samples
     */
    private long getTotalSamples() {
        Pageable pageable = new PageRequest(0, 1);
        Collection<Filter> filters = Collections.emptyList();
        Collection<String> domains = Collections.emptyList();
        Page<Sample> samplePage = samplePageService.getSamplesByText("", filters, domains, pageable, null);
        return samplePage.getTotalElements();
    }
}
