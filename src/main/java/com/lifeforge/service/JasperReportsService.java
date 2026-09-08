package com.lifeforge.service;

import com.lifeforge.config.DatabaseConfig;
import com.lifeforge.model.Goal;
import com.lifeforge.model.Recommendation;
import com.lifeforge.model.User;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.export.HtmlExporter;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleHtmlExporterOutput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;

import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Compiles the JasperReports JRXML templates from the classpath,
 * fills them with data (and a live JDBC connection) and exports
 * the result as PDF or HTML.
 *
 * <p>The LifeForgeHealthReport template is an auto-generated report for a
 * single user profile (profile + goal + calorie/hydration figures +
 * recommendation count). The SavedRecommendationsReport template lists
 * everything the user has saved from the recommendation hub.</p>
 */
public class JasperReportsService {

    private static final String REPORTS_RESOURCE_BASE = "/reports/";
    public static final String HEALTH_REPORT = "LifeForgeHealthReport";
    public static final String SAVED_RECOMMENDATIONS_REPORT = "SavedRecommendationsReport";

    private final ExportService exportService;

    public JasperReportsService(ExportService exportService) {
        this.exportService = exportService;
    }

    /**
     * Builds the health report data from a user profile, goal and
     * recommendations, then exports it to PDF.
     */
    public void exportHealthReportToPdf(User user, Goal goal, List<Recommendation> recommendations, Path outputFile)
            throws SQLException, JRException {
        exportToPdf(HEALTH_REPORT, exportService.buildReportData(user, goal, recommendations), outputFile);
    }

    /**
     * Builds the health report data from a user profile, goal and
     * recommendations, then exports it to HTML.
     */
    public void exportHealthReportToHtml(User user, Goal goal, List<Recommendation> recommendations, Path outputFile)
            throws SQLException, JRException {
        exportToHtml(HEALTH_REPORT, exportService.buildReportData(user, goal, recommendations), outputFile);
    }

    /**
     * Exports a report template to PDF. The report is filled using the
     * supplied parameter map plus a live connection from DatabaseConfig.
     */
    public void exportToPdf(String reportName, Map<String, Object> parameters, Path outputFile)
            throws SQLException, JRException {
        export(reportName, parameters, outputFile, true);
    }

    /**
     * Exports a report template to HTML. The report is filled using the
     * supplied parameter map plus a live connection from DatabaseConfig.
     */
    public void exportToHtml(String reportName, Map<String, Object> parameters, Path outputFile)
            throws SQLException, JRException {
        export(reportName, parameters, outputFile, false);
    }

    private void export(String reportName, Map<String, Object> parameters, Path outputFile, boolean asPdf)
            throws SQLException, JRException {
        JasperReport compiledReport = compile(reportName);
        try (Connection connection = DatabaseConfig.getConnection()) {
            JasperPrint jasperPrint = JasperFillManager.fillReport(compiledReport, parameters, connection);
            export(jasperPrint, outputFile, asPdf);
        }
    }

    private JasperReport compile(String reportName) throws JRException {
        InputStream template = getClass().getResourceAsStream(REPORTS_RESOURCE_BASE + reportName + ".jrxml");
        if (template == null) {
            throw new JRException("Report template not found on classpath: " + REPORTS_RESOURCE_BASE + reportName + ".jrxml");
        }
        return JasperCompileManager.compileReport(template);
    }

    private void export(JasperPrint jasperPrint, Path outputFile, boolean asPdf) throws JRException {
        if (asPdf) {
            JRPdfExporter exporter = new JRPdfExporter();
            exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(outputFile.toFile()));
            exporter.exportReport();
        } else {
            HtmlExporter exporter = new HtmlExporter();
            exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
            exporter.setExporterOutput(new SimpleHtmlExporterOutput(outputFile.toFile()));
            exporter.exportReport();
        }
    }
}