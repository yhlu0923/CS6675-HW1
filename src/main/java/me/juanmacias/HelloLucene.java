package me.juanmacias;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

public class HelloLucene {

    private static final String SEED_URL = "https://cc.gatech.edu";

    private static final HashSet<String> visitedUrls = new HashSet<>();
    private static final Queue<String> urlQueue = new LinkedList<>();
    private static Directory index;

    public static void main(String[] args) throws IOException {
        // 0. Specify the analyzer for tokenizing text.
        // The same analyzer should be used for indexing and searching

        // 1. create the index
        index = new ByteBuffersDirectory();

        // Initialize URL queue
        urlQueue.add(SEED_URL);

        String[] queryStrings = {"georgia", "page", "new", "Senior", "graduate"};

        System.out.println("Indexed 100 documents.");
        addMultipleDocuments(100);
        displayExampleKeywords(queryStrings);

        System.out.println("Indexed 1 documents.");
        addMultipleDocuments(1);
        displayExampleKeywords(queryStrings);
        
        // 
        measurePerformance();
    }

    private static void indexDocument(IndexWriter writer, String url, String content) throws IOException {
        Document doc = new Document();
        doc.add(new StringField("url", url, Field.Store.YES));
        doc.add(new TextField("content", content, Field.Store.YES));
        writer.addDocument(doc);
    }

    private static void addMultipleDocuments(int count) throws IOException {

        StandardAnalyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        IndexWriter w = new IndexWriter(index, config);

        int startSize = visitedUrls.size();

        while (!urlQueue.isEmpty() && visitedUrls.size() < startSize + count) {
            String url = urlQueue.poll();
            if (!visitedUrls.contains(url)) {
                visitedUrls.add(url);
                // System.out.println("Crawling: " + url);

                try {
                    org.jsoup.nodes.Document htmlDoc = Jsoup.connect(url).get();
                    String content = htmlDoc.text();
                    indexDocument(w, url, content);

                    for (Element link : htmlDoc.select("a[href]")) {
                        String nextUrl = link.absUrl("href");
                        if (nextUrl.startsWith("http://") || nextUrl.startsWith("https://")) {
                            if (!visitedUrls.contains(nextUrl)) {
                                urlQueue.add(nextUrl);
                            }
                        }
                    }
                } catch (IOException e) {
                    // System.err.println("Failed to fetch URL: " + url);
                }
            }
        }

        w.close();
        // System.out.println("Crawling completed. Indexed " + visitedUrls.size() + " URLs.");
    }

    private static void displayExampleKeywords(String[] queryStrings) throws IOException {

        // the "title" arg specifies the default field to use
        // when no field is explicitly specified in the query.

        // 3. search
        int hitsPerPage = 1000;
        IndexReader reader = DirectoryReader.open(index);
        IndexSearcher searcher = new IndexSearcher(reader);
        
        for (String queryString : queryStrings) {
            // long startTime = System.currentTimeMillis();

            Query query = null;
            try {
                query = new QueryParser("content", new StandardAnalyzer()).parse(queryString);
            } catch (org.apache.lucene.queryparser.classic.ParseException e) {
                e.printStackTrace();
            }
    
            TopDocs docs = searcher.search(query, hitsPerPage);
            ScoreDoc[] hits = docs.scoreDocs;

            // long endTime = System.currentTimeMillis();
            
            // 4. display results
            System.out.println("Found " + hits.length + " hits." + "\t" + queryString);
            // System.out.println("The search takes " + (endTime - startTime) + " ms.");
            // for (int i = 0; i < hits.length; ++i) {
            //     int docId = hits[i].doc;
            //     Document d = searcher.doc(docId);
            //     System.out.println((i + 1) + ". " + d.get("url") + "\t");
            // }
        }

        // reader can only be closed when there
        // is no need to access the documents any more.
        reader.close();
    }

    private static void measurePerformance() throws IOException {
        String[] queryStrings = {"georgia", "page", "new", "Senior", "graduate"};
        int[] docCounts = {100, 200, 300};

        for (int count : docCounts) {
            long startTime = System.currentTimeMillis();
            addMultipleDocuments(count);
            long endTime = System.currentTimeMillis();
            System.out.println("Indexed " + count + " documents in " + (endTime - startTime) + " ms.");
            displayExampleKeywords(queryStrings);
        }
    }
}
