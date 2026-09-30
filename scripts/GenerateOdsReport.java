package com.lifeforge.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class GenerateOdsReport {

    public static void main(String[] args) {
        try {
            File targetDir = new File("c:/Users/Reaksmey/Documents/Foundation-class/java/LifeForgeMySelfCode");
            File odsFile = new File(targetDir, "LifeForge_Research_Report.ods");
            File odtFile = new File(targetDir, "LifeForge_Research_Report.odt");

            buildOds(odsFile);
            System.out.println("Created ODS successfully: " + odsFile.getAbsolutePath());

            buildOdt(odtFile);
            System.out.println("Created ODT successfully: " + odtFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void buildOds(File outFile) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(outFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // 1. mimetype (MUST be first, uncompressed STORED)
            byte[] mimeBytes = "application/vnd.oasis.opendocument.spreadsheet".getBytes(StandardCharsets.US_ASCII);
            ZipEntry mimeEntry = new ZipEntry("mimetype");
            mimeEntry.setMethod(ZipEntry.STORED);
            mimeEntry.setSize(mimeBytes.length);
            mimeEntry.setCompressedSize(mimeBytes.length);
            CRC32 crc = new CRC32();
            crc.update(mimeBytes);
            mimeEntry.setCrc(crc.getValue());
            zos.putNextEntry(mimeEntry);
            zos.write(mimeBytes);
            zos.closeEntry();

            // 2. META-INF/manifest.xml
            writeZipTextEntry(zos, "META-INF/manifest.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2">
                  <manifest:file-entry manifest:full-path="/" manifest:version="1.2" manifest:media-type="application/vnd.oasis.opendocument.spreadsheet"/>
                  <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
                  <manifest:file-entry manifest:full-path="styles.xml" manifest:media-type="text/xml"/>
                  <manifest:file-entry manifest:full-path="meta.xml" manifest:media-type="text/xml"/>
                </manifest:manifest>
                """);

            // 3. meta.xml
            writeZipTextEntry(zos, "meta.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <office:document-meta xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                  xmlns:meta="urn:oasis:names:tc:opendocument:xmlns:meta:1.0"
                  xmlns:dc="http://purl.org/dc/elements/1.1/"
                  office:version="1.2">
                  <office:meta>
                    <dc:title>LifeForge Research Report</dc:title>
                    <dc:creator>Chhom Chanreaksmey</dc:creator>
                    <dc:date>2025-03-01T00:00:00</dc:date>
                  </office:meta>
                </office:document-meta>
                """);

            // 4. styles.xml
            writeZipTextEntry(zos, "styles.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <office:document-styles xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                  xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
                  xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
                  xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
                  xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
                  office:version="1.2">
                  <office:styles>
                    <style:default-style style:family="table-cell">
                      <style:text-properties fo:font-size="10pt" fo:font-family="Segoe UI, Calibri, Arial, sans-serif"/>
                    </style:default-style>
                  </office:styles>
                </office:document-styles>
                """);

            // 5. content.xml
            String contentXml = generateOdsContentXml();
            writeZipTextEntry(zos, "content.xml", contentXml);
        }
    }

    private static void buildOdt(File outFile) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(outFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // 1. mimetype
            byte[] mimeBytes = "application/vnd.oasis.opendocument.text".getBytes(StandardCharsets.US_ASCII);
            ZipEntry mimeEntry = new ZipEntry("mimetype");
            mimeEntry.setMethod(ZipEntry.STORED);
            mimeEntry.setSize(mimeBytes.length);
            mimeEntry.setCompressedSize(mimeBytes.length);
            CRC32 crc = new CRC32();
            crc.update(mimeBytes);
            mimeEntry.setCrc(crc.getValue());
            zos.putNextEntry(mimeEntry);
            zos.write(mimeBytes);
            zos.closeEntry();

            // 2. manifest.xml
            writeZipTextEntry(zos, "META-INF/manifest.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2">
                  <manifest:file-entry manifest:full-path="/" manifest:version="1.2" manifest:media-type="application/vnd.oasis.opendocument.text"/>
                  <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
                  <manifest:file-entry manifest:full-path="styles.xml" manifest:media-type="text/xml"/>
                  <manifest:file-entry manifest:full-path="meta.xml" manifest:media-type="text/xml"/>
                </manifest:manifest>
                """);

            // 3. meta.xml
            writeZipTextEntry(zos, "meta.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <office:document-meta xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                  xmlns:meta="urn:oasis:names:tc:opendocument:xmlns:meta:1.0"
                  xmlns:dc="http://purl.org/dc/elements/1.1/"
                  office:version="1.2">
                  <office:meta>
                    <dc:title>LifeForge Research Report</dc:title>
                    <dc:creator>Chhom Chanreaksmey</dc:creator>
                    <dc:date>2025-03-01T00:00:00</dc:date>
                  </office:meta>
                </office:document-meta>
                """);

            // 4. styles.xml
            writeZipTextEntry(zos, "styles.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <office:document-styles xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                  xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
                  xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
                  xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
                  office:version="1.2">
                  <office:styles>
                    <style:default-style style:family="paragraph">
                      <style:text-properties fo:font-size="11pt" fo:font-family="Segoe UI, Calibri, Arial, sans-serif"/>
                    </style:default-style>
                  </office:styles>
                </office:document-styles>
                """);

            // 5. content.xml
            String contentXml = generateOdtContentXml();
            writeZipTextEntry(zos, "content.xml", contentXml);
        }
    }

    private static void writeZipTextEntry(ZipOutputStream zos, String path, String content) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        zos.putNextEntry(entry);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        zos.write(bytes);
        zos.closeEntry();
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    private static String generateOdsContentXml() {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
              xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
              xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
              xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
              xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
              office:version="1.2">
              <office:automatic-styles>
                <style:style style:name="co1" style:family="table-column">
                  <style:table-column-properties style:column-width="3.5cm"/>
                </style:style>
                <style:style style:name="co2" style:family="table-column">
                  <style:table-column-properties style:column-width="7.0cm"/>
                </style:style>
                <style:style style:name="co3" style:family="table-column">
                  <style:table-column-properties style:column-width="26.0cm"/>
                </style:style>
                <style:style style:name="title-style" style:family="table-cell">
                  <style:table-cell-properties fo:background-color="#1E3A8A" fo:padding="8pt"/>
                  <style:text-properties fo:font-size="15pt" fo:font-weight="bold" fo:color="#FFFFFF"/>
                </style:style>
                <style:style style:name="header-style" style:family="table-cell">
                  <style:table-cell-properties fo:background-color="#2563EB" fo:padding="5pt"/>
                  <style:text-properties fo:font-size="11pt" fo:font-weight="bold" fo:color="#FFFFFF"/>
                </style:style>
                <style:style style:name="section-style" style:family="table-cell">
                  <style:table-cell-properties fo:background-color="#DBEAFE" fo:padding="4pt"/>
                  <style:text-properties fo:font-size="11pt" fo:font-weight="bold" fo:color="#1E40AF"/>
                </style:style>
                <style:style style:name="label-style" style:family="table-cell">
                  <style:table-cell-properties fo:background-color="#F8FAFC" fo:padding="4pt"/>
                  <style:text-properties fo:font-size="10pt" fo:font-weight="bold" fo:color="#0F172A"/>
                </style:style>
                <style:style style:name="content-style" style:family="table-cell">
                  <style:table-cell-properties fo:wrap-option="wrap" fo:padding="4pt"/>
                  <style:text-properties fo:font-size="10pt" fo:color="#334155"/>
                </style:style>
              </office:automatic-styles>
              <office:body>
                <office:spreadsheet>
            """);

        // SHEET 1: Full Research Report
        sb.append("<table:table table:name=\"Research Report\">\n");
        sb.append("  <table:table-column table:style-name=\"co1\"/>\n");
        sb.append("  <table:table-column table:style-name=\"co2\"/>\n");
        sb.append("  <table:table-column table:style-name=\"co3\"/>\n");

        // Title
        appendOdsRow(sb, "title-style", "LifeForge", "Personalized Health & Lifestyle Recommendation System", "Research Report - Foundation Generation 3rd Course (ISTAD)");
        appendOdsRow(sb, "header-style", "Section", "Subsection / Field", "Detailed Research Content");

        // Cover Details
        appendOdsRow(sb, "section-style", "COVER INFO", "Institution (Khmer)", "វិទ្យាស្ថានវិទ្យាសាស្ត្រ និងបច្ចេកវិទ្យាអភិវឌ្ឍន៍កម្រិតខ្ពស់");
        appendOdsRow(sb, "content-style", "", "Institution (English)", "Institute of Science and Technology Advanced Development (ISTAD)");
        appendOdsRow(sb, "content-style", "", "Course", "Foundation Generation 3rd Course");
        appendOdsRow(sb, "content-style", "", "Project Name", "LifeForge: Personalized Health & Lifestyle Recommendation System");
        appendOdsRow(sb, "content-style", "", "Author / Team Lead", "Mr. Chhom Chanreaksmey (reaksmeychan427@gmail.com)");
        appendOdsRow(sb, "content-style", "", "Mentors", "Mr. Ing Davann, Mr. Kim Chansokpheng");
        appendOdsRow(sb, "content-style", "", "Date & Address", "March 2025 | #24, St. 562, Sangkat Boeung Kak I, Khan Toul Kork, Phnom Penh, Cambodia");

        // Abstract
        appendOdsRow(sb, "section-style", "ABSTRACT", "Executive Summary", 
            "LifeForge is a data-driven, personalized health and lifestyle recommendation system developed in Java. The application delivers evidence-based guidance—including Basal Metabolic Rate (BMR), Total Daily Energy Expenditure (TDEE), optimal hydration targets, and categorized lifestyle blueprints—tailored to individual user profiles, activity levels, and health goals. Built upon a robust layered Model-View-Controller (MVC) and Data Access Object (DAO) architecture, LifeForge integrates a rule-based recommendation engine, a multi-factor match scoring algorithm, and an intelligent explanation service that demystifies why specific recommendations are generated. The application features a modern Terminal User Interface (TUI) powered by TUI4J and JLine 3 with full UTF-8 and ANSI support, administrative audit logging, secure BCrypt password hashing, and dynamic PDF export capabilities powered by JasperReports.");

        // Section 1: Introduction
        appendOdsRow(sb, "section-style", "1. INTRODUCTION", "Problem Domain", 
            "In modern society, maintaining physical wellness has become increasingly complex. While health and lifestyle information is abundant across the internet, much of it is fragmented, conflicting, or overly generic. Many commercial wellness solutions focus exclusively on calorie tracking and intrusive daily logging, which often leads to user fatigue and abandonment.");
        appendOdsRow(sb, "content-style", "1. INTRODUCTION", "Proposed Solution", 
            "LifeForge was developed to bridge this gap by providing an actionable, personalized, and explainable health recommendation platform. Instead of tedious manual logging, LifeForge analyzes core physiological metrics (age, gender, height, weight, activity level) and primary health objectives (e.g., weight loss, muscle gain, cardiovascular endurance, sleep enhancement, and general wellness) to construct tailored daily lifestyle blueprints. The system incorporates algorithmic transparency through a multi-factor Match Score and an AI/Rule-based explanation subsystem, allowing users to understand the physiological rationale behind every recommendation.");

        // Section 2: Background
        appendOdsRow(sb, "section-style", "2. BACKGROUND", "Origin & Context", 
            "LifeForge was developed in 2025 as a capstone project for the Foundation Course at the Institute of Science and Technology Advanced Development (ISTAD). The motivation stemmed from the need for an accessible, low-latency, and privacy-focused health guidance tool that operates reliably without requiring heavy web browsers or external cloud dependencies.");
        appendOdsRow(sb, "content-style", "2. BACKGROUND", "Technical Rationale", 
            "Java 21 was selected as the foundational programming language due to its type safety, performance, rich ecosystem, and enterprise-grade concurrency model. To eliminate the overhead of traditional graphical frontends while retaining modern aesthetic interactivity, the system leverages a Terminal User Interface (TUI) pattern inspired by Bubble Tea, paired with PostgreSQL for enterprise relational data management. LifeForge adheres to clean code principles, SOLID design standards, and comprehensive separation of concerns across data access, business logic, and presentation layers.");

        // Section 3: System Architecture
        appendOdsRow(sb, "section-style", "3. ARCHITECTURE", "3.1 Selected Technology", 
            "• Language: Java 21 (LTS)\n• IDE: IntelliJ IDEA\n• UI: TUI4J (Bubble Tea port for Java) + JLine 3.27.0 (ANSI rendering) + JNA Platform 5.15.0 (Windows console API)\n• Database: PostgreSQL 42.7.4 with raw JDBC connection pooling\n• Security: jBCrypt 0.4 for salted password hashing & SecureRandom for password reset tokens\n• Reporting: JasperReports 6.21.3 (JRXML compilation and PDF rendering)\n• Build System: Gradle with custom fat-jar and UTF-8 console task configurations");
        appendOdsRow(sb, "content-style", "3. ARCHITECTURE", "3.2 Design Patterns", 
            "1. Model-View-Controller (MVC): Clear separation between data models, business controllers, and terminal views.\n2. Data Access Object (DAO): Encapsulates raw SQL queries and connection handling (UserDao, GoalDao, RecommendationDao, etc.).\n3. Strategy & Rule Engine: The RecommendationEngine and RecommendationPriorityResolver dynamically score and prioritize recommendations.\n4. AppContext / Service Locator: Injects singletons and manages component lifecycle at application startup.");
        appendOdsRow(sb, "content-style", "3. ARCHITECTURE", "3.3 Entity Relationship Diagram", 
            "8 Relational Tables: users, goals, user_goals, recommendation_categories, recommendations, saved_recommendations, audit_logs, password_resets. Designed with 3NF normalization, foreign key cascade constraints, and B-tree indexes for fast queries.");

        // Section 4: Features and Evaluation
        appendOdsRow(sb, "section-style", "4. EVALUATION", "4.1 Project Strengths", 
            "1. Scientific Calorie & Energy Calculations: Accurate BMR via Mifflin-St Jeor equation and TDEE based on activity multipliers.\n2. Multi-Goal Prioritization: Resolves primary and secondary focus areas based on user goals.\n3. Explainable Match Score: Transparent mathematical breakdown (40% Profile Completeness, 40% Goal Alignment, 20% Activity Synergy).\n4. AI & Rule-Based Explanation: Provides evidence-based rationale behind recommendations.\n5. Modern Terminal UI: Responsive ANSI layout, arrow-key navigation, split panes, and UTF-8 box drawing.\n6. Enterprise Security: Salted BCrypt password hashing, expiring password reset tokens, and audit logging.\n7. PDF Export: JasperReports integration for exporting personalized blueprints and saved recommendations.");
        appendOdsRow(sb, "content-style", "4. EVALUATION", "4.2 Project Weaknesses", 
            "1. Terminal-Only Client: No native mobile or web interface.\n2. No Wearable Sync: Requires manual physiological metric entry.\n3. Local PostgreSQL Dependency: Requires a running database instance.\n4. Synchronous Interaction: Lacks background push notifications or email/SMS alerts.");

        // Section 5: Challenges and Future Improvements
        appendOdsRow(sb, "section-style", "5. CHALLENGES", "5.1 Challenges Encountered", 
            "1. Windows Console Unicode & ANSI Rendering: Overcame CP437/CP1252 character corruption through JVM UTF-8 flags and TerminalSupport fallback.\n2. Terminal Keystroke Capture: Built custom LifeForgeRunner to hook directly into Windows CONIN$ stream to bypass Gradle input piping.\n3. Headless JasperReports: Configured headless AWT flags (-Djava.awt.headless=true) and font mappings to generate PDFs from console.");
        appendOdsRow(sb, "content-style", "5. CHALLENGES", "5.2 Future Improvements", 
            "1. RESTful API & Web/Mobile Client: Build a Spring Boot REST API for web (React) and mobile (Flutter).\n2. Wearable Health Integration: Ingest data from Apple HealthKit, Google Fit, and Garmin.\n3. Machine Learning Recommender: Upgrade to hybrid collaborative/content-based filtering.\n4. Notification Daemon: Background service for hydration and lifestyle reminders.");

        // Section 6: Conclusion & References
        appendOdsRow(sb, "section-style", "6. CONCLUSION", "Final Summary", 
            "LifeForge demonstrates the successful implementation of a personalized health and lifestyle recommendation system built with modern Java 21 and relational database persistence. By prioritizing algorithmic transparency, scientific formulas (Mifflin-St Jeor), and explainable recommendations over intrusive daily logging, LifeForge offers users a practical, privacy-conscious path toward achieving their wellness goals. Supported by robust security mechanisms, an elegant terminal interface, and dynamic JasperReports generation, the project establishes a solid foundation for extensible, data-driven health software solutions.");
        appendOdsRow(sb, "section-style", "REFERENCES", "Citations", 
            "1. Mifflin, M. D., St Jeor, S. T., et al. (1990). A new predictive equation for resting energy expenditure in healthy individuals. Am J Clin Nutr, 51(2), 241-247.\n2. Oracle Corp. (2023). Java SE 21 Documentation. https://docs.oracle.com/en/java/javase/21/\n3. PostgreSQL Global Development Group. (2024). PostgreSQL 16 Documentation. https://www.postgresql.org/docs/\n4. TUI4J Development Team. (2024). TUI4J: Terminal UI for Java. https://github.com/WilliamAGH/tui4j\n5. JLine Development Team. (2024). JLine 3. https://github.com/jline/jline3\n6. Jaspersoft. (2024). JasperReports Library 6.21.3 Reference Manual. https://community.jaspersoft.com/");

        sb.append("</table:table>\n");

        // SHEET 2: Technology Stack & Layered Architecture
        sb.append("<table:table table:name=\"Architecture &amp; Tech\">\n");
        sb.append("  <table:table-column table:style-name=\"co1\"/>\n");
        sb.append("  <table:table-column table:style-name=\"co2\"/>\n");
        sb.append("  <table:table-column table:style-name=\"co3\"/>\n");
        appendOdsRow(sb, "title-style", "Layer / Component", "Technology / Library", "Role & Description in LifeForge");
        appendOdsRow(sb, "header-style", "Layer", "Selected Tech", "Implementation Details");
        appendOdsRow(sb, "content-style", "Core Runtime", "Java 21 LTS (OpenJDK Temurin)", "Modern Java features: Switch expressions, Pattern matching, Text blocks, Virtual Threads");
        appendOdsRow(sb, "content-style", "User Interface (View)", "TUI4J 0.3.3 + JLine 3.27.0", "Terminal UI based on Elm/Bubble Tea architecture. Renders ANSI escape codes, box-drawing borders, and interactive menus.");
        appendOdsRow(sb, "content-style", "Native Console Driver", "JNA Platform 5.15.0", "Directly binds to Windows kernel32.dll (SetConsoleOutputCP, CONIN$) for reliable arrow keys & UTF-8 display.");
        appendOdsRow(sb, "content-style", "Database Persistence", "PostgreSQL 42.7.4 (JDBC)", "ACID-compliant relational database storing users, goals, recommendations, audit logs, and tokens.");
        appendOdsRow(sb, "content-style", "Security & Hashing", "jBCrypt 0.4", "Salted password hashing (work factor 12) protecting credentials against rainbow tables.");
        appendOdsRow(sb, "content-style", "Document Export", "JasperReports 6.21.3", "Dynamic PDF generation from JRXML templates for health blueprints and bookmarked recommendations.");
        appendOdsRow(sb, "content-style", "Build Automation", "Gradle 8.x", "Build script managing dependencies, JVM console arguments, and fat-jar creation.");
        appendOdsRow(sb, "content-style", "Controller Layer", "BaseController & Subcontrollers", "Orchestrates request handling, authentication state, validation, and session navigation.");
        appendOdsRow(sb, "content-style", "Service Layer", "RecommendationEngine, CalorieService", "Encapsulates business rules, Mifflin-St Jeor calculation, priority ranking, and AI explanation logic.");
        appendOdsRow(sb, "content-style", "Data Access Layer (DAO)", "UserDao, GoalDao, RecDao, etc.", "Executes parameterized SQL queries with clean transaction handling.");
        sb.append("</table:table>\n");

        // SHEET 3: Database ERD Schema
        sb.append("<table:table table:name=\"Database ERD Schema\">\n");
        sb.append("  <table:table-column table:style-name=\"co1\"/>\n");
        sb.append("  <table:table-column table:style-name=\"co2\"/>\n");
        sb.append("  <table:table-column table:style-name=\"co3\"/>\n");
        appendOdsRow(sb, "title-style", "Table Name", "Key Columns & Data Types", "Constraints, Foreign Keys & Description");
        appendOdsRow(sb, "header-style", "Table", "Columns & Types", "Details");
        appendOdsRow(sb, "content-style", "users", "id BIGSERIAL (PK), full_name VARCHAR(100), username VARCHAR(100), email VARCHAR(150), password_hash VARCHAR(255), age INT, gender VARCHAR(30), height_cm DOUBLE, weight_kg DOUBLE, activity_level VARCHAR(50), role VARCHAR(30), blocked BOOLEAN, created_at/updated_at TIMESTAMP", "Stores registered user accounts, biometric profile, and role (USER / ADMIN). Email and username are UNIQUE.");
        appendOdsRow(sb, "content-style", "goals", "id BIGSERIAL (PK), code VARCHAR(50) UNIQUE, name VARCHAR(100), description TEXT, active BOOLEAN", "Master table of health goals (e.g. LOSE_WEIGHT, BUILD_MUSCLE, SLEEP_BETTER, GENERAL_WELLNESS).");
        appendOdsRow(sb, "content-style", "user_goals", "id BIGSERIAL (PK), user_id BIGINT (FK -> users.id), goal_id BIGINT (FK -> goals.id), active BOOLEAN, selected_at TIMESTAMP", "Many-to-many junction linking users to their active and historical health goals.");
        appendOdsRow(sb, "content-style", "recommendation_categories", "id BIGSERIAL (PK), name VARCHAR(100), description TEXT, parent_category_id BIGINT (FK -> self), display_order INT", "Hierarchical categories for recommendations (Nutrition, Physical Activity, Recovery & Sleep, Mindset).");
        appendOdsRow(sb, "content-style", "recommendations", "id BIGSERIAL (PK), goal_id BIGINT (FK -> goals.id), category_id BIGINT (FK -> categories.id), activity_level VARCHAR(50), title VARCHAR(150), description TEXT, recommended_actions TEXT, suggested_target VARCHAR(150), examples TEXT, important_notes TEXT", "Core recommendation repository containing evidence-based advice tailored to goal and activity level.");
        appendOdsRow(sb, "content-style", "saved_recommendations", "id BIGSERIAL (PK), user_id BIGINT (FK -> users.id), recommendation_id BIGINT (FK -> recommendations.id), saved_at TIMESTAMP", "User bookmarked recommendations. Enforces UNIQUE(user_id, recommendation_id).");
        appendOdsRow(sb, "content-style", "audit_logs", "id BIGSERIAL (PK), actor_user_id BIGINT (FK -> users.id), action VARCHAR(50), target_type VARCHAR(50), target_id BIGINT, details TEXT, created_at TIMESTAMP", "Immutable system audit trail tracking logins, updates, admin actions, and password changes.");
        appendOdsRow(sb, "content-style", "password_resets", "id BIGSERIAL (PK), user_id BIGINT (FK -> users.id), code_hash VARCHAR(255), expires_at TIMESTAMP, attempt_count INT, used BOOLEAN, status VARCHAR(20)", "Manages secure one-time verification tokens for password recovery with expiry and attempt limits.");
        sb.append("</table:table>\n");

        // SHEET 4: Scientific Formulas & Calculations
        sb.append("<table:table table:name=\"Scientific Formulas\">\n");
        sb.append("  <table:table-column table:style-name=\"co1\"/>\n");
        sb.append("  <table:table-column table:style-name=\"co2\"/>\n");
        sb.append("  <table:table-column table:style-name=\"co3\"/>\n");
        appendOdsRow(sb, "title-style", "Calculation", "Formula / Algorithm", "Clinical / Technical Rationale");
        appendOdsRow(sb, "header-style", "Formula Name", "Mathematical Equation", "Explanation");
        appendOdsRow(sb, "content-style", "BMR (Male)", "BMR = (10 × weight_kg) + (6.25 × height_cm) - (5 × age) + 5", "Mifflin-St Jeor equation: The clinical standard for calculating resting caloric expenditure in adult men.");
        appendOdsRow(sb, "content-style", "BMR (Female)", "BMR = (10 × weight_kg) + (6.25 × height_cm) - (5 × age) - 161", "Mifflin-St Jeor equation: Adjusted for differences in female body composition and lean muscle mass.");
        appendOdsRow(sb, "content-style", "TDEE Multiplier", "TDEE = BMR × ActivityMultiplier\n• Sedentary: 1.2\n• Lightly Active: 1.375\n• Moderately Active: 1.55\n• Very Active: 1.725\n• Extra Active / Athlete: 1.9", "Scales basal metabolic rate to account for daily non-exercise physical activity and workout energy expenditure.");
        appendOdsRow(sb, "content-style", "Calorie Target", "Target = TDEE + GoalAdjustment (Safety floor: 1200 kcal)\n• Lose Weight: -500 kcal/day (~0.5 kg loss/wk)\n• Gain Weight: +400 kcal/day\n• Build Muscle: +300 kcal/day (hypertrophy surplus)", "Calculates sustainable caloric balance without extreme restriction.");
        appendOdsRow(sb, "content-style", "Daily Hydration", "Water = (weight_kg × 0.033) + ActivityAddition\n• Sedentary: +0.0 L\n• Lightly Active: +0.3 L\n• Moderately Active: +0.5 L\n• Very Active: +0.8 L\n• Athlete: +1.0 L", "Estimates daily water intake based on body weight plus hydration compensation for sweat loss.");
        appendOdsRow(sb, "content-style", "Recommendation Match Score", "MatchScore = (Completeness × 40%) + (GoalAlignment × 40%) + (Synergy × 20%)\n• Completeness: Ratio of populated profile fields\n• GoalAlignment: Presence of goal-specific recommendations\n• Synergy: Match between activity level and recommendation intensity", "Algorithmic transparency metric providing users an intuitive percentage of personalization relevance.");
        sb.append("</table:table>\n");

        sb.append("""
                </office:spreadsheet>
              </office:body>
            </office:document-content>
            """);
        return sb.toString();
    }

    private static void appendOdsRow(StringBuilder sb, String style, String col1, String col2, String col3) {
        sb.append("  <table:table-row>\n");
        sb.append("    <table:table-cell table:style-name=\"").append(style).append("\" office:value-type=\"string\">\n");
        sb.append("      <text:p>").append(escapeXml(col1)).append("</text:p>\n");
        sb.append("    </table:table-cell>\n");
        sb.append("    <table:table-cell table:style-name=\"").append(style).append("\" office:value-type=\"string\">\n");
        sb.append("      <text:p>").append(escapeXml(col2)).append("</text:p>\n");
        sb.append("    </table:table-cell>\n");
        sb.append("    <table:table-cell table:style-name=\"").append(style).append("\" office:value-type=\"string\">\n");
        // For multiline text, split by newline and emit multiple text:p
        String[] lines = col3.split("\n");
        for (String line : lines) {
            sb.append("      <text:p>").append(escapeXml(line)).append("</text:p>\n");
        }
        sb.append("    </table:table-cell>\n");
        sb.append("  </table:table-row>\n");
    }

    private static String generateOdtContentXml() {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
              xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
              xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
              xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
              office:version="1.2">
              <office:automatic-styles>
                <style:style style:name="doc-title" style:family="paragraph">
                  <style:paragraph-properties fo:text-align="center" fo:margin-top="15pt" fo:margin-bottom="5pt"/>
                  <style:text-properties fo:font-size="20pt" fo:font-weight="bold" fo:color="#1E3A8A"/>
                </style:style>
                <style:style style:name="doc-subtitle" style:family="paragraph">
                  <style:paragraph-properties fo:text-align="center" fo:margin-bottom="15pt"/>
                  <style:text-properties fo:font-size="13pt" fo:font-style="italic" fo:color="#4B5563"/>
                </style:style>
                <style:style style:name="doc-h1" style:family="paragraph">
                  <style:paragraph-properties fo:margin-top="14pt" fo:margin-bottom="4pt" fo:border-bottom="1pt solid #2563EB"/>
                  <style:text-properties fo:font-size="14pt" fo:font-weight="bold" fo:color="#1E40AF"/>
                </style:style>
                <style:style style:name="doc-h2" style:family="paragraph">
                  <style:paragraph-properties fo:margin-top="10pt" fo:margin-bottom="3pt"/>
                  <style:text-properties fo:font-size="12pt" fo:font-weight="bold" fo:color="#1F2937"/>
                </style:style>
                <style:style style:name="doc-body" style:family="paragraph">
                  <style:paragraph-properties fo:margin-top="3pt" fo:margin-bottom="6pt" fo:line-height="130%"/>
                  <style:text-properties fo:font-size="10.5pt" fo:color="#374151"/>
                </style:style>
                <style:style style:name="doc-quote" style:family="paragraph">
                  <style:paragraph-properties fo:margin-left="15pt" fo:margin-right="15pt" fo:margin-top="6pt" fo:margin-bottom="6pt" fo:background-color="#F3F4F6" fo:padding="8pt"/>
                  <style:text-properties fo:font-size="10pt" fo:font-style="italic" fo:color="#1F2937"/>
                </style:style>
              </office:automatic-styles>
              <office:body>
                <office:text>
            """);

        appendOdtP(sb, "doc-title", "វិទ្យាស្ថានវិទ្យាសាស្ត្រ និងបច្ចេកវិទ្យាអភិវឌ្ឍន៍កម្រិតខ្ពស់");
        appendOdtP(sb, "doc-subtitle", "Institute of Science and Technology Advanced Development (ISTAD)");
        appendOdtP(sb, "doc-title", "LifeForge: Personalized Health &amp; Lifestyle Recommendation System");
        appendOdtP(sb, "doc-subtitle", "A Research Report in Partial Fulfilment of the Requirement for Foundation Generation 3rd Course\nMarch 2025");

        appendOdtP(sb, "doc-body", "Author: Mr. Chhom Chanreaksmey (reaksmeychan427@gmail.com)\nMentors: Mr. Ing Davann, Mr. Kim Chansokpheng\nAddress: #24, St. 562, Sangkat Boeung Kak I, Khan Toul Kork, Phnom Penh, Cambodia");

        appendOdtP(sb, "doc-h1", "Abstract");
        appendOdtP(sb, "doc-quote", "LifeForge is a data-driven, personalized health and lifestyle recommendation system developed in Java. The application delivers evidence-based guidance—including Basal Metabolic Rate (BMR), Total Daily Energy Expenditure (TDEE), optimal hydration targets, and categorized lifestyle blueprints—tailored to individual user profiles, activity levels, and health goals. Built upon a robust layered Model-View-Controller (MVC) and Data Access Object (DAO) architecture, LifeForge integrates a rule-based recommendation engine, a multi-factor match scoring algorithm, and an intelligent explanation service that demystifies why specific recommendations are generated. The application features a modern Terminal User Interface (TUI) powered by TUI4J and JLine 3 with full UTF-8 and ANSI support, administrative audit logging, secure BCrypt password hashing, and dynamic PDF export capabilities powered by JasperReports.");

        appendOdtP(sb, "doc-h1", "1. Introduction");
        appendOdtP(sb, "doc-body", "In modern society, maintaining physical wellness has become increasingly complex. While health and lifestyle information is abundant across the internet, much of it is fragmented, conflicting, or overly generic. Many commercial wellness solutions focus exclusively on calorie tracking and intrusive daily logging, which often leads to user fatigue and abandonment.");
        appendOdtP(sb, "doc-body", "LifeForge was developed to bridge this gap by providing an actionable, personalized, and explainable health recommendation platform. Instead of tedious manual logging, LifeForge analyzes core physiological metrics (age, gender, height, weight, activity level) and primary health objectives (e.g., weight loss, muscle gain, cardiovascular endurance, sleep enhancement, and general wellness) to construct tailored daily lifestyle blueprints. The system incorporates algorithmic transparency through a multi-factor Match Score and an AI/Rule-based explanation subsystem, allowing users to understand the physiological rationale behind every recommendation.");

        appendOdtP(sb, "doc-h1", "2. Background");
        appendOdtP(sb, "doc-body", "LifeForge was developed in 2025 as a capstone project for the Foundation Course at the Institute of Science and Technology Advanced Development (ISTAD). The motivation stemmed from the need for an accessible, low-latency, and privacy-focused health guidance tool that operates reliably without requiring heavy web browsers or external cloud dependencies.");
        appendOdtP(sb, "doc-body", "Java 21 was selected as the foundational programming language due to its type safety, performance, rich ecosystem, and enterprise-grade concurrency model. To eliminate the overhead of traditional graphical frontends while retaining modern aesthetic interactivity, the system leverages a Terminal User Interface (TUI) pattern inspired by Bubble Tea, paired with PostgreSQL for enterprise relational data management. LifeForge adheres to clean code principles, SOLID design standards, and comprehensive separation of concerns across data access, business logic, and presentation layers.");

        appendOdtP(sb, "doc-h1", "3. System Architecture");
        appendOdtP(sb, "doc-h2", "3.1 Selected Technology");
        appendOdtP(sb, "doc-body", "• Language: Java 21 (LTS)\n• IDE: IntelliJ IDEA\n• UI: TUI4J (Bubble Tea port for Java) + JLine 3.27.0 (ANSI rendering) + JNA Platform 5.15.0 (Windows console API)\n• Database: PostgreSQL 42.7.4 with raw JDBC connection pooling\n• Security: jBCrypt 0.4 for salted password hashing & SecureRandom for password reset tokens\n• Reporting: JasperReports 6.21.3 (JRXML compilation and PDF rendering)\n• Build System: Gradle with custom fat-jar and UTF-8 console task configurations\n• Testing: JUnit 5 (JUnit Jupiter)");

        appendOdtP(sb, "doc-h2", "3.2 Design Patterns");
        appendOdtP(sb, "doc-body", "1. Model-View-Controller (MVC): Clear separation between data models, business controllers, and terminal views.\n2. Data Access Object (DAO): Encapsulates raw SQL queries and connection handling (UserDao, GoalDao, RecommendationDao, etc.).\n3. Strategy & Rule Engine: The RecommendationEngine and RecommendationPriorityResolver dynamically score and prioritize recommendations.\n4. AppContext / Service Locator: Injects singletons and manages component lifecycle at application startup.");

        appendOdtP(sb, "doc-h2", "3.3 Entity Relationship Diagram (ERD)");
        appendOdtP(sb, "doc-body", "The persistence layer comprises 8 relational tables designed with third normal form (3NF) principles:\n- users: User credentials, biometrics, activity level, and roles (USER/ADMIN).\n- goals: Master list of predefined fitness and lifestyle goals.\n- user_goals: Many-to-many junction mapping active and past user goals.\n- recommendation_categories: Hierarchical taxonomy of recommendation topics.\n- recommendations: Evidence-based actions, targets, and notes tailored by goal & activity.\n- saved_recommendations: User bookmark repository.\n- audit_logs: System-wide audit trail for compliance and admin moderation.\n- password_resets: Hashed, expiring single-use verification tokens for account recovery.");

        appendOdtP(sb, "doc-h1", "4. Features and Evaluation");
        appendOdtP(sb, "doc-h2", "4.1 Project Strengths");
        appendOdtP(sb, "doc-body", "• Scientific Calorie Calculations: Mifflin-St Jeor BMR and TDEE based on physical activity multipliers.\n• Multi-Goal Prioritization: Dynamically resolves primary and supporting focus areas.\n• Explainable Match Score: Transparent 40/40/20% scoring breakdown.\n• Modern TUI: Arrow-key navigation, responsive split-screens, and UTF-8 box drawing.\n• Enterprise Security: Salted BCrypt password hashing and audit trails.\n• PDF Export: JasperReports integration for saving blueprints and recommendations.");

        appendOdtP(sb, "doc-h2", "4.2 Project Weaknesses");
        appendOdtP(sb, "doc-body", "• Terminal-Only Client: Lacks a graphical web or mobile frontend.\n• No Wearable Sync: Requires manual metric input.\n• Local Database Dependency: Requires an active PostgreSQL service.\n• Synchronous Notification Model: Lacks background push or SMS notifications.");

        appendOdtP(sb, "doc-h1", "5. Challenges and Future Improvements");
        appendOdtP(sb, "doc-h2", "5.1 Challenges Encountered");
        appendOdtP(sb, "doc-body", "1. Windows Console Unicode & ANSI: Solved CP437/CP1252 corruption via UTF-8 JVM flags and TerminalSupport fallback.\n2. Gradle Keystroke Capture: Implemented LifeForgeRunner hooking directly into Windows CONIN$.\n3. Headless JasperReports: Configured headless AWT flags to render PDF reports from the console.");

        appendOdtP(sb, "doc-h2", "5.2 Future Improvements");
        appendOdtP(sb, "doc-body", "• RESTful API & Web/Mobile Client (Spring Boot + React + Flutter).\n• Wearable Health Integration (Apple HealthKit, Google Fit, Garmin).\n• Machine Learning Recommender Hybrid.\n• Background Notification Worker for hydration and meal schedules.");

        appendOdtP(sb, "doc-h1", "6. Conclusion");
        appendOdtP(sb, "doc-body", "LifeForge demonstrates the successful implementation of a personalized health and lifestyle recommendation system built with modern Java 21 and relational database persistence. By prioritizing algorithmic transparency, scientific formulas (Mifflin-St Jeor), and explainable recommendations over intrusive daily logging, LifeForge offers users a practical, privacy-conscious path toward achieving their wellness goals. Supported by robust security mechanisms, an elegant terminal interface, and dynamic JasperReports generation, the project establishes a solid foundation for extensible, data-driven health software solutions.");

        appendOdtP(sb, "doc-h1", "References");
        appendOdtP(sb, "doc-body", "1. Mifflin, M. D., St Jeor, S. T., et al. (1990). A new predictive equation for resting energy expenditure in healthy individuals. Am J Clin Nutr, 51(2), 241-247.\n2. Oracle Corp. (2023). Java SE 21 Documentation. https://docs.oracle.com/en/java/javase/21/\n3. PostgreSQL Global Development Group. (2024). PostgreSQL 16 Documentation. https://www.postgresql.org/docs/\n4. TUI4J Development Team. (2024). TUI4J: Terminal UI for Java. https://github.com/WilliamAGH/tui4j\n5. JLine Development Team. (2024). JLine 3. https://github.com/jline/jline3\n6. Jaspersoft. (2024). JasperReports Library 6.21.3 Reference Manual. https://community.jaspersoft.com/");

        sb.append("""
                </office:text>
              </office:body>
            </office:document-content>
            """);
        return sb.toString();
    }

    private static void appendOdtP(StringBuilder sb, String style, String text) {
        String[] lines = text.split("\n");
        for (String line : lines) {
            sb.append("  <text:p text:style-name=\"").append(style).append("\">")
              .append(escapeXml(line))
              .append("</text:p>\n");
        }
    }
}
