package com.mycompany.huub_de_pga;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.json.*;
import okhttp3.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

public class Huub_De_PGA extends JFrame {

    // ==============================
    // CONFIGURATIE
    // ==============================

    private static final String API_KEY = System.getenv("OPENAI_API_KEY");
    private static final OkHttpClient CLIENT = new OkHttpClient();

    private static final String PERSONEELSGIDS_VERSIE =
            "Personeelsgids BU Talentclass versie 2024.1";

    private JPanel chatPanel;
    private JScrollPane scrollPane;
    private JTextField inputField;
    private Image backgroundImage;

    private final List<JSONObject> conversationHistory = new ArrayList<>();
    private final List<Chunk> chunks = new ArrayList<>();

    // ==============================
    // DATASTRUCTUUR
    // ==============================

    static class Chunk {
        String text;
        List<Double> embedding;
        int page;

        // Slaat per tekstdeel de broninhoud, embedding-vector en paginaverwijzing op.
        Chunk(String text, List<Double> embedding, int page) {
            this.text = text;
            this.embedding = embedding;
            this.page = page;
        }
    }

    // ==============================
    // CONSTRUCTOR
    // ==============================

    // Initialiseert het hoofdvenster, laadt de gidsdata en zet de chatinterface op.
    public Huub_De_PGA() throws Exception {

        setTitle("Huub – HR Chatbot (Verlof)");
        setSize(1100, 700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        backgroundImage = new ImageIcon("qquestlogoHoe gaa.png").getImage();

        loadGuide();
        
        setupChatPanel();
        setupInputPanel();

        setVisible(true);

        addBubble("Welkom! Ik ben Huub, jouw HR-assistent (domein: verlof).", false);
        addBubble("Gebruikte bron: " + PERSONEELSGIDS_VERSIE, false);
    }

    // ==============================
    // UI
    // ==============================

    // Bouwt het scrollbare chatgedeelte op en tekent de achtergrondafbeelding.
    private void setupChatPanel() {

        chatPanel = new JPanel() {
            // Tekent de achtergrond telkens wanneer het paneel opnieuw wordt gerenderd.
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(backgroundImage, 0, 0, null);
            }
        };

        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));


        scrollPane = new JScrollPane(chatPanel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        add(scrollPane, BorderLayout.CENTER);
    }

    // Bouwt het invoerveld met verzendknop en koppelt beide aan dezelfde verzendactie.
    private void setupInputPanel() {

        inputField = new JTextField();
        JButton sendButton = new JButton("Verstuur");

        sendButton.setFocusPainted(false);
        sendButton.setBackground(new Color(0, 90, 160));
        sendButton.setForeground(Color.WHITE);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(new EmptyBorder(12, 12, 12, 12));
        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> send());
        inputField.addActionListener(e -> send());
    }

    // ==============================
    // CHAT
    // ==============================

    // Leest de gebruikersvraag, toont die in de UI en haalt een antwoord op.
    private void send() {

        String question = inputField.getText().trim();
        if (question.isEmpty()) return;

        addBubble(question, true);
        inputField.setText("");

        new Thread(() -> {
            try {

                String answer = ask(question);

                if (answer == null || answer.trim().isEmpty()) {
                    answer = "Sorry, ik kon geen antwoord genereren.";
                }

                String finalAnswer = answer;

                SwingUtilities.invokeLater(() ->
                        addBubble(finalAnswer, false));

            } catch (Exception ex) {

                ex.printStackTrace();

                String msg = ex.getMessage() == null
                        ? "Onbekende fout (check console)"
                        : ex.getMessage();

                SwingUtilities.invokeLater(() ->
                        addBubble("Er ging iets mis: " + msg, false));
            }
        }).start();
    }

    // Voegt een chatballon toe voor gebruiker of assistent en scrolt naar beneden.
    private void addBubble(String text, boolean user) {

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);

        // ===== Tekst splitsen in antwoord en disclaimer =====
String antwoord = text;
String disclaimer = "";

if (!user && text.contains("Disclaimer:")) {
    int index = text.indexOf("Disclaimer:");
    antwoord = text.substring(0, index).trim();
    disclaimer = text.substring(index).trim();
}

// ===== HTML layout maken =====
String htmlText;

if (!user && !disclaimer.isEmpty()) {
    htmlText =
        "<html>" +
        "<div style='font-family:Segoe UI; font-size:13px; width:650px'>" +
        antwoord.replace("\n", "<br>") +
        "</div>" +
        "<div style='margin-top:20px; font-size:10px; color:gray; text-align:left;'>" +
        disclaimer.replace("\n", "<br>") +
        "</div>" +
        "</html>";
} else {
    htmlText =
        "<html>" +
        "<div style='font-family:Segoe UI; font-size:13px; width: 650px'>" +
        text.replace("\n", "<br>") +
        "</div>" +
        "</html>";
}
JTextPane bubble = new JTextPane();
bubble.setContentType("text/html");
bubble.setText(htmlText);
bubble.setEditable(false);
bubble.setBorder(new EmptyBorder(14, 20, 14, 20));

// ⭐ BELANGRIJK — zorgt dat tekst wrapt binnen venster
bubble.setMaximumSize(new Dimension(700, Integer.MAX_VALUE));
bubble.setPreferredSize(null);
bubble.setSize(new Dimension(700, Short.MAX_VALUE));



        if (user) {
            bubble.setBackground(new Color(0, 90, 160));
            bubble.setForeground(Color.WHITE);
        } else {
            bubble.setBackground(new Color(255, 255, 255, 235));
            bubble.setForeground(Color.BLACK);
        }

        JPanel container = new JPanel(new BorderLayout());
        container.setOpaque(false);
        container.setBorder(new EmptyBorder(8, 40, 8, 40));
        container.add(bubble, BorderLayout.CENTER);

        row.add(container, BorderLayout.CENTER);
        chatPanel.add(row);
        chatPanel.revalidate();

        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = scrollPane.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    // ==============================
    // PDF + EMBEDDINGS
    // ==============================

    // Leest de PDF per pagina uit, splitst tekst in chunks en maakt embeddings voor zoekwerk.
    private void loadGuide() throws Exception {

        PDDocument doc = Loader.loadPDF(new File("personeelsgids.pdf"));
        PDFTextStripper stripper = new PDFTextStripper();

        for (int page = 1; page <= doc.getNumberOfPages(); page++) {

            stripper.setStartPage(page);
            stripper.setEndPage(page);

            String pageText = stripper.getText(doc);
            List<String> parts = chunkText(pageText, 800);

            for (String part : parts)
                chunks.add(new Chunk(part, embed(part), page));
        }

        doc.close();

        if (chunks.isEmpty())
            throw new RuntimeException("Geen tekst uit personeelsgids geladen.");
    }

    // Splitst lange tekst op in woordblokken van vaste grootte voor verwerking.
    private static List<String> chunkText(String text, int size) {

        List<String> result = new ArrayList<>();
        String[] words = text.split("\\s+");

        for (int i = 0; i < words.length; i += size) {
            result.add(String.join(" ",
                    Arrays.copyOfRange(words, i, Math.min(words.length, i + size))));
        }

        return result;
    }

    // Roept de embedding-API aan en retourneert de numerieke vector van de invoertekst.
    private static List<Double> embed(String input) throws Exception {

        JSONObject body = new JSONObject()
                .put("model", "text-embedding-3-small")
                .put("input", input);

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/embeddings")
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(),
                        MediaType.parse("application/json")))
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {

            if (!response.isSuccessful())
                throw new RuntimeException("Embedding API error: " + response.code());

            JSONObject json = new JSONObject(response.body().string());

            JSONArray arr = json.getJSONArray("data")
                    .getJSONObject(0)
                    .getJSONArray("embedding");

            List<Double> vector = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++)
                vector.add(arr.getDouble(i));

            return vector;
        }
    }

    // Berekent de cosine similarity tussen twee vectors om inhoudelijke overeenkomst te meten.
    private double cosine(List<Double> a, List<Double> b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            na += a.get(i) * a.get(i);
            nb += b.get(i) * b.get(i);
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    // Zoekt de meest relevante chunks voor de vraag op basis van embedding-similarity.
    private List<Chunk> search(String query) throws Exception {

        // Maak embedding van de vraag
        List<Double> qVec = embed(query);

        // Maak lijst van (chunk + similarity score)
        List<Map.Entry<Chunk, Double>> scoredChunks = new ArrayList<>();

        for (Chunk c : chunks) {
            double score = cosine(c.embedding, qVec);
            scoredChunks.add(Map.entry(c, score));
        }

        // Sorteer op hoogste similarity eerst
        scoredChunks.sort((a, b)
                -> Double.compare(b.getValue(), a.getValue()));

        // Alleen chunks die voldoende relevant zijn
        double MIN_SIMILARITY = 0.3;

        List<Chunk> results = new ArrayList<>();

        for (Map.Entry<Chunk, Double> entry : scoredChunks) {
            if (entry.getValue() < MIN_SIMILARITY) {
                break;
            }
            results.add(entry.getKey());

            if (results.size() >= 6) {
                break;
            }
        }

        return results;
    }


    // ==============================
    // OPENAI CHAT
    // ==============================

    // Stelt context en prompt samen, vraagt de chat-API om antwoord en bewaart conversatiehistorie.
    private String ask(String question) throws Exception {

        List<Chunk> topChunks = search(question);

        StringBuilder contextText = new StringBuilder();
        for (Chunk c : topChunks) {
            contextText.append("PAGINA ")
                    .append(c.page)
                    .append(": ")
                    .append(c.text)
                    .append("\n\n");
        }

        String contextString = contextText.toString();
        

        // 👉 JOUW PROMPT EXACT
        String systemPrompt =

"# ROLE " +
"Je bent Huub, een gespecialiseerde HR-assistent die uitsluitend vragen beantwoordt over verlofregelingen op basis van de verstrekte PERSONEELSGIDS. " +

"# DOEL " +
"Verstrek accurate, feitelijke informatie over verlof (soorten, aanvragen, saldo, etc.) aan medewerkers. " +

"# CONSTRAINTS (STRIKTE REGELS) " +
"1. Source Grounding: Gebruik ALLEEN de informatie tussen de <context> tags. " +
"Als het antwoord daar niet staat, zeg je: \"Ik kan deze informatie niet terugvinden in de personeelsgids. Neem contact op met HR voor verdere ondersteuning.\" " +

"2. Scope: Behandel alleen verlof-gerelateerde zaken. Voor vragen over salaris, leaseauto's of ontslag antwoord je: " +
"\"Mijn expertise is beperkt tot verlofregelingen. Voor deze vraag verwijs ik u graag door naar de relevante afdeling.\" " +

"3. Geen Hallucinaties: Verzin nooit paginanummers, citaten, data of percentages die niet letterlijk in de tekst staan. " +

"4. Bronvermelding (verplicht): " +
"Als informatie uit de PERSONEELSGIDS wordt gebruikt, moet je: " +
"- het juiste paginanummer uit de context vermelden, " +
"- geen pagina vermelden als deze niet expliciet in de context staan. " +

"5. Toon: Professioneel, zakelijk, behulpzaam maar kortaf waar nodig om feitelijkheid te bewaren. " +

"# STAPSGEWIJZE VERWERKING (Chain of Thought) " +
"Voordat je antwoordt, doorloop je intern deze stappen: " +
"- Stap 1: Analyseer of de vraag (geheel of gedeeltelijk) over verlof gaat. " +
"- Stap 2: Zoek in de <context> naar de specifieke secties die over dit onderwerp gaan. " +
"- Stap 3: Controleer of er tegenstrijdigheden zijn in de tekst. " +
"- Stap 4: Formuleer het antwoord en identificeer de bron inclusief paginanummer en relevante passage. " +

"# OUTPUT FORMAT " +
"Hanteer strikt de volgende structuur: " +

"Antwoord: [Geef hier het feitelijke antwoord. Bij gemengde vragen: beantwoord alleen het verlof-deel.] " +

"Bron: [Vermeld hoofdstuktitel of sectienaam EN paginanummer uit de gids. Indien niet gevonden: N.v.t.] " +

"Disclaimer: Deze informatie is mogelijk niet volledig of niet actueel. Deze informatie is niet juridisch bindend. Raadpleeg bij twijfel altijd HR. " +

"<context> " +
"{{hier de tekst uit de personeelsgids}} " +
"</context> " +

"<vraag_gebruiker> " +
"{{vraag}} " +
"</vraag_gebruiker>";

        String finalSystemPrompt = systemPrompt
                .replace("{{hier de tekst uit de personeelsgids}}", contextString)
                .replace("{{vraag}}", question);

        JSONArray messages = new JSONArray()
                .put(new JSONObject()
                        .put("role", "system")
                        .put("content", finalSystemPrompt));

        JSONObject body = new JSONObject()
                .put("model", "gpt-4o-mini")
                .put("messages", messages)
                .put("temperature", 0)
                .put("top_p", 0);

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(),
                        MediaType.parse("application/json")))
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {

            if (!response.isSuccessful())
                throw new RuntimeException("Chat API error: " + response.code());

            JSONObject json = new JSONObject(response.body().string());

            String answer = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

            conversationHistory.add(new JSONObject().put("role", "user").put("content", question));
            conversationHistory.add(new JSONObject().put("role", "assistant").put("content", answer));

            if (conversationHistory.size() > 12)
                conversationHistory.subList(0, conversationHistory.size() - 12).clear();

            return answer;
        }
    }

    // ==============================

    // Start de Swing-applicatie en opent het chatbotvenster op de event-dispatch-thread.
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeLater(() -> {
            try {
                new Huub_De_PGA();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}