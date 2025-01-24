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
    private static final int MAX_URLS = 10;

    private static final HashSet<String> visitedUrls = new HashSet<>();
    private static final Queue<String> urlQueue = new LinkedList<>();
    private static Directory index;

    public static void main(String[] args) throws IOException {
        // 0. Specify the analyzer for tokenizing text.
        // The same analyzer should be used for indexing and searching
        StandardAnalyzer analyzer = new StandardAnalyzer();

        // 1. create the index
        index = new ByteBuffersDirectory();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);

        IndexWriter w = new IndexWriter(index, config);

        // Initialize URL queue
        urlQueue.add(SEED_URL);

        while (!urlQueue.isEmpty() && visitedUrls.size() < MAX_URLS) {
            String url = urlQueue.poll();
            if (!visitedUrls.contains(url)) {
                visitedUrls.add(url);
                System.out.println("Crawling: " + url);

                try {
                    org.jsoup.nodes.Document htmlDoc = Jsoup.connect(url).get();
                    String content = htmlDoc.text();
                    indexDocument(w, url, content);

                    for (Element link : htmlDoc.select("a[href]")) {
                        String nextUrl = link.absUrl("href");
                        if (!visitedUrls.contains(nextUrl)) {
                            urlQueue.add(nextUrl);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Failed to fetch URL: " + url);
                }
            }
        }

        w.close();
        System.out.println("Crawling completed. Indexed " + visitedUrls.size() + " URLs.");

        // 2. query
        String queryString = args.length > 0 ? args[0] : "health";

        // the "title" arg specifies the default field to use
        // when no field is explicitly specified in the query.
        Query query = null;
        try {
            query = new QueryParser("content", analyzer).parse(queryString);
        } catch (org.apache.lucene.queryparser.classic.ParseException e) {
            e.printStackTrace();
        }

        // 3. search
        int hitsPerPage = 10;
        IndexReader reader = DirectoryReader.open(index);
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs docs = searcher.search(query, hitsPerPage);
        ScoreDoc[] hits = docs.scoreDocs;

        // 4. display results
        System.out.println("Found " + hits.length + " hits.");
        for (int i = 0; i < hits.length; ++i) {
            int docId = hits[i].doc;
            Document d = searcher.doc(docId);
            System.out.println((i + 1) + ". " + d.get("url") + "\t");
        }

        // reader can only be closed when there
        // is no need to access the documents any more.
        reader.close();
    }

    private static void addDoc(IndexWriter w, String title, String isbn) throws IOException {
        Document doc = new Document();
        doc.add(new TextField("title", title, Field.Store.YES));

        // use a string field for isbn because we don't want it tokenized
        doc.add(new StringField("isbn", isbn, Field.Store.YES));
        w.addDocument(doc);
    }

    private static void indexDocument(IndexWriter writer, String url, String content) throws IOException {
        Document doc = new Document();
        // doc.add(new StringField("url", url, Field.Store.YES));
        doc.add(new StringField("url", url, Field.Store.YES));
        doc.add(new TextField("content", content, Field.Store.YES));
        writer.addDocument(doc);
    }

}
