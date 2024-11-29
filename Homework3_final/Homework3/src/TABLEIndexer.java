import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONObject;
import org.json.JSONArray;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.charfilter.HTMLStripCharFilterFactory;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.core.StopFilterFactory;
import org.apache.lucene.analysis.core.WhitespaceTokenizerFactory;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilterFactory;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilterFactory;
import org.apache.lucene.analysis.snowball.SnowballPorterFilterFactory;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizerFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class TABLEIndexer {

    private static String INDEX_DIR = "index_path";
    static String TABLES_DIR = "cleaned_tables_path";
    private static Path path = Paths.get(INDEX_DIR);
    private static int counter = 0;
    private static int counter2 = 0;
    
    public static void main(String[] args) {
        try {
            // Create the index
            Directory dir = FSDirectory.open(path);
            
            // Define a default analyzer (e.g., StandardAnalyzer)
            Analyzer defaultAnalyzer = new StandardAnalyzer();

            // Create specific analyzers for certain fields
            Analyzer captionAnalyzer = CustomAnalyzer.builder()
            		.withTokenizer(WhitespaceTokenizerFactory.class) // Tokenize based on spaces
            		.addTokenFilter(LowerCaseFilterFactory.class) // Convert to lowercase
            		.addTokenFilter(ASCIIFoldingFilterFactory.class) // Handle accents by transforming them
            		.addTokenFilter(StopFilterFactory.class,"ignoreCase", "true") // Remove stopwords in English
            		.addTokenFilter(SnowballPorterFilterFactory.class, "language", "English") // Stemming
            		.build();	
            Analyzer tableAnalyzer = CustomAnalyzer.builder()
            		.addCharFilter(HTMLStripCharFilterFactory.class) // Remove HTML tags
                    .withTokenizer(WhitespaceTokenizerFactory.class) // Tokenize based on spaces
                    .addTokenFilter(LowerCaseFilterFactory.class) // Convert to lowercase
                    .addTokenFilter(ASCIIFoldingFilterFactory.class) // Remove accents
                    .build();
            Analyzer referencesAnalyzer = CustomAnalyzer.builder() // To be defined
		            .withTokenizer(StandardTokenizerFactory.class) // Appropriate tokenization
		            .addTokenFilter(LowerCaseFilterFactory.class) // Convert to lowercase
		            .addTokenFilter(ASCIIFoldingFilterFactory.class) // Remove accents
		            .addTokenFilter(WordDelimiterGraphFilterFactory.class, 
		                            "splitOnCaseChange", "1", 
		                            "generateWordParts", "1") // Handle compound terms
		            .addTokenFilter(StopFilterFactory.class, "ignoreCase", "true") // Stopword list
		            .build();

            // Associate each field with its specific analyzer
            Map<String, Analyzer> analyzerPerField = new HashMap<>();
            analyzerPerField.put("caption", captionAnalyzer);
            analyzerPerField.put("table", tableAnalyzer);
            analyzerPerField.put("references", referencesAnalyzer);
            
            // Create a PerFieldAnalyzerWrapper to handle analyzers per field
            Analyzer multiFieldAnalyzer = new PerFieldAnalyzerWrapper(defaultAnalyzer, analyzerPerField);
            
            IndexWriterConfig config = new IndexWriterConfig(multiFieldAnalyzer);
            IndexWriter writer = new IndexWriter(dir, config);
            
            // Record the start time
            Instant startTime = Instant.now();
            
            // Index JSON files
            File[] files = new File(TABLES_DIR).listFiles((dir1, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                
                    indexJSONfile(writer, file);
                    counter += 1;
                }
            }
            writer.commit();
            writer.close();
            
            Instant endTime = Instant.now();
            // Calculate the time taken and the indexing rate
            long timeElapsed = Duration.between(startTime, endTime).toMillis(); // time in milliseconds
            double timeElapsedSeconds = timeElapsed / 1000.0;
            
            System.out.println("Indexing completed of " + counter + " in " + timeElapsedSeconds + "s.");
            System.out.println(counter2);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String JSONnormalizer(Object value) {
        String result = "";
        if (value instanceof String) {
            // If "table" is a string, retrieve it directly
            result = (String) value;
        } else if (value instanceof JSONArray) {
            // If "table" is an array, get the first element which should be a string
            JSONArray tableArray = (JSONArray) value;
            String res = "";
            for (int i = 0; i < tableArray.length(); i++) {
                // Retrieve the current element
                Object value2 = tableArray.get(i);
                if (value2 instanceof String) {
                    res = (String) value2;
                } else if (value2 instanceof JSONArray) {
                    JSONArray tableArray2 = (JSONArray) value2;
                    for (int j = 0; j < tableArray2.length(); j++) {
                        String res2 = tableArray2.getString(j);
                        res = res + res2;
                    }
                    
                    // Add the element to the string, with a separator (e.g., ", ")
                    result = result + res;
                    
                    // Add a separator between elements (if not the last element)
                    if (i < tableArray.length() - 1) {
                        result = result + ("; ");
                    }
                }
            }
        }
        return result;
    }

    private static void indexJSONfile(IndexWriter writer, File file) throws IOException {
        try {

            String content = new String(Files.readAllBytes(file.toPath()));
            // Convert the string to a JSON object
            JSONObject jsonObject = new JSONObject(content);

            // Loop through the keys of the JSON object
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String footnotes = "";
                String reference = "";
                String caption = "";
                String table = "";
                
                String key = keys.next();
                JSONObject value = jsonObject.optJSONObject(key); // Retrieve the associated value
                if (value != null && value.has("caption") && value.has("table")) {
                    // Extract the caption
                    Object captionValue = value.get("caption");
                    caption = JSONnormalizer(captionValue);
                    // Extract the table
                    Object tableValue = value.get("table");
                    table = JSONnormalizer(tableValue);
                    if (value.has("footnotes")) {
                        // Extract the footnotes
                        Object footnotesValue = value.get("footnotes");
                        footnotes = JSONnormalizer(footnotesValue);
                    }
                    if (value.has("references")) {
                        // Extract the references
                        Object referenceValue = value.get("references");
                        reference = JSONnormalizer(referenceValue);
                    }
                }
        
                String references = reference;
                if (footnotes.length() > 0) {
                    references = references + "; " + footnotes;
                }
                
                // Create a Lucene document
                Document doc = new Document();

                // Add fields to the document with specific analyzers
                doc.add(new TextField("caption", caption, Field.Store.YES));
                doc.add(new TextField("table", table, Field.Store.YES));
                doc.add(new TextField("references", references, Field.Store.YES));
                writer.addDocument(doc);
                
            }
            System.out.println("Indexed file: " + file.getName());
        } catch (Exception e) {
            System.err.println("Error indexing file " + file.getName() + ": " + e.getMessage());
            counter2++;
        }
    }
}
