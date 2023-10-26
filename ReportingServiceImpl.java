package ma.hps.powercard.administration.base.serviceimpl;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TimeZone;
import java.util.Map.Entry;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.engine.export.JRTextExporter;
import net.sf.jasperreports.engine.export.JRXlsExporter;
import net.sf.jasperreports.engine.export.JRXlsExporterParameter;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.engine.export.JRCsvExporterParameter;
import net.sf.jasperreports.engine.util.JRLoader;
import java.util.Date;
import java.io.File;
import org.fornax.cartridges.sculptor.framework.errorhandling.ServiceContext;
import java.math.BigDecimal;
import ma.hps.exception.OurException;
import ma.hps.powercard.administration.base.serviceapi.Pwc_report_parametersVO;
import ma.hps.powercard.administration.base.serviceapi.ReportingVO;
import ma.hps.powercard.administration.base.serviceapi.Report_param_recVO;
import ma.hps.powercard.administration.base.serviceapi.GeneretedReportVO;
import ma.hps.powercard.administration.base.serviceapi.GeneretedReportTaskVO;
import ma.hps.powercard.administration.base.serviceapi.FailedReportVO;
import ma.hps.powercard.administration.base.serviceapi.Pcard_tasksVO;
import ma.hps.powercard.administration.base.serviceapi.Pcard_tasks_reportVO;
import ma.hps.powercard.administration.base.serviceapi.Pwc_reports_user_setupVO;
import ma.hps.powercard.administration.base.serviceapi.Pwc_generate_report_histVO;
import ma.hps.powercard.administration.base.serviceapi.Pwc_generated_report_fileVO;
import ma.hps.powercard.administration.base.serviceapi.Pwc_bank_report_parametersVO;
import ma.hps.powercard.administration.base.serviceapi.Execute_report_functionInVO;
import ma.hps.powercard.administration.base.serviceapi.Execute_report_functionOutVO;
import ma.hps.powercard.compliance.serviceapi.Ressource_bundleVO;
import ma.hps.powercard.compliance.utils.ReportRB;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.PreparedStatement;
import ma.hps.powercard.administration.base.serviceimpl.DynamicReportBuilder;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Implementation of ReportingService.
 */
@Lazy
@Service("reportingService")
public class ReportingServiceImpl extends ReportingServiceImplBase {

	private static final Log LOG = LogFactory.getLog(ReportingServiceImpl.class);

	Map<String, Object> params;
	private String status;
	private String bankCode;
	private String user;
	private String reportLanguage;
	private String[] arrayLanguage = new String[4];
	private String report_code;
	private String reportType;
	private boolean saveToServer;
	private String screenReportType;
	private String reportFormat;
	private String executionSource;
	private static Locale reportlocale = null;
	private static DateFormat formatter = new SimpleDateFormat("ddMMyyyyHHmmss");
	private static DateFormat processingDateFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
	private static GeneretedReportTaskVO generetedReportTaskVO;
	private static List<GeneretedReportVO> generetedReportList = new ArrayList<GeneretedReportVO>();
	private static Collection<FailedReportVO> failedReportSet = new HashSet<FailedReportVO>();
	private static GeneretedReportVO generetedReportVO;
	private FailedReportVO failedReportVO;
	private Date generationDate;
	private Collection<Pwc_generated_report_fileVO> pwc_generated_report_file_col;
	private Collection<String> inReportCodeList = new HashSet<String>();

	public ReportingServiceImpl() {
	}

	public ResourceBundle prepareBundles(ServiceContext ctx, ReportingVO reportingVO) throws Exception {

		Ressource_bundleVO ressource_bundleVO = new Ressource_bundleVO();
		ressource_bundleVO.setLocale_chain((String) reportingVO.getParams().get("locale_chain"));
		Collection<String> inBundle = new ArrayList<String>();
		inBundle.add((String) reportingVO.getSourceFileName());
		inBundle.add("CommonsReport");
		ressource_bundleVO.setInBundle(inBundle);
		List<Ressource_bundleVO> lRessource_bundle = this.getRessource_bundleService()
				.searchRessource_bundleService(ctx, ressource_bundleVO);
		Dictionary dictionary = new Hashtable();
		for (Ressource_bundleVO vo : lRessource_bundle) {
			dictionary.put(vo.getKey_val(), vo.getValue());
		}
		ReportRB rb = new ReportRB(Locale.ENGLISH, dictionary);
		return rb;
	}

	private boolean doGenerateReport(String taskStatus, String taskReportStatus) {

		if (taskReportStatus.equalsIgnoreCase("N")) {
			return false;
		} else if (taskReportStatus.equalsIgnoreCase("Y")
				|| (taskReportStatus.equalsIgnoreCase("S") && taskStatus.equalsIgnoreCase("S"))
				|| (taskReportStatus.equalsIgnoreCase("U") && taskStatus.equalsIgnoreCase("U"))) {
			return true;
		}
		return true;
	}

	synchronized public GeneretedReportTaskVO executeTaskReportsService(ServiceContext ctx, ReportingVO reportingVO)
			throws Exception {
		LOG.info("Execute task reports ...");
		generetedReportTaskVO = new GeneretedReportTaskVO();
		generetedReportList = new ArrayList<GeneretedReportVO>();
		failedReportSet = new HashSet<FailedReportVO>();
		String language = reportingVO.getLanguage();
		bankCode = reportingVO.getBank_code();
		saveToServer = reportingVO.isSaveToServer();
		user = reportingVO.getUser();
		screenReportType = reportingVO.getReportType();
		executionSource = reportingVO.getExecution_source();
		reportFormat = reportingVO.getFormat();
		inReportCodeList = new HashSet<String>();

		String report_name = "";
		String task_group = reportingVO.getTask_group();
		String task_name = reportingVO.getTask_name();

		Pwc_reports_user_setupVO searchPwc_reports_user_setup = new Pwc_reports_user_setupVO();
		searchPwc_reports_user_setup.setUser_code(user);
		searchPwc_reports_user_setup.setExecution_flag("Y");
		List<Pwc_reports_user_setupVO> resultPwc_reports_user_setupVOList = this.getPwc_reports_user_setupService()
				.searchPwc_reports_user_setupService(ctx, searchPwc_reports_user_setup);

		for (Pwc_reports_user_setupVO pwc_reports_user_setupVO : resultPwc_reports_user_setupVOList) {
			inReportCodeList.add(pwc_reports_user_setupVO.getReport_code());
		}

		Pcard_tasksVO searchPcardTasksVO = new Pcard_tasksVO();
		searchPcardTasksVO.setTask_group_code(task_group);
		searchPcardTasksVO.setTask_name(task_name);
		searchPcardTasksVO.setBank_code(bankCode);

		List<Pcard_tasksVO> resultPcardTasksVOList = this.getPcard_tasksService().searchPcard_tasksService(ctx,
				searchPcardTasksVO);
		Pcard_tasksVO pcard_tasksVO = resultPcardTasksVOList.get(0);
		String taskStatus = pcard_tasksVO.getStatus();

		Pcard_tasks_reportVO searchPcardTasksReportVO = new Pcard_tasks_reportVO();
		searchPcardTasksReportVO.setTask_group(task_group);
		searchPcardTasksReportVO.setTask_name(task_name);
		searchPcardTasksReportVO.setBank_code(bankCode);
		searchPcardTasksReportVO.setInReportCode(inReportCodeList);
		List<Pcard_tasks_reportVO> resultPcardTasksReportVOList = this.getPcard_tasks_reportService()
				.searchPcard_tasks_reportService(ctx, searchPcardTasksReportVO);

		bankCode = "ALL";
		Pwc_report_parametersVO searchPwc_report_parametersVO = null;
		for (Pcard_tasks_reportVO pcardTasksReportVO : resultPcardTasksReportVOList) {
			if (doGenerateReport(taskStatus, pcardTasksReportVO.getPrint_status())) {
				pwc_generated_report_file_col = new LinkedList<Pwc_generated_report_fileVO>();
				searchPwc_report_parametersVO = new Pwc_report_parametersVO();
				searchPwc_report_parametersVO.setReport_module(pcardTasksReportVO.getModule_code());
				searchPwc_report_parametersVO.setReport_code(pcardTasksReportVO.getReport_code());
				List<Pwc_report_parametersVO> resultPwcReportParametersVO = this.getPwc_report_parametersService()
						.searchPwc_report_parametersService(ctx, searchPwc_report_parametersVO);
				Pwc_report_parametersVO pwcReportParametersVO = resultPwcReportParametersVO.get(0);

				Long generateReportHistSeq = null;
				try {
					report_code = pwcReportParametersVO.getReport_code();
					report_name = pwcReportParametersVO.getReport_name();
					prepareParametersMap(pwcReportParametersVO);
					generateReportHistSeq = createPwcGenerateReportHist(ctx, pwcReportParametersVO, reportingVO);
					executeBeforeReport(ctx, pwcReportParametersVO, report_code, language);
					executeReport(ctx, generateReportHistSeq, pwcReportParametersVO);
					status = "S";
					pwcReportParametersVO.setStatus(status);
					updatePwcGenerateReportHist(ctx, generateReportHistSeq, pwcReportParametersVO);
					this.getPwc_report_parametersService().updatePwc_report_parametersService(ctx,
							pwcReportParametersVO);
				} catch (Exception e) {
					prepareFailedReportList(report_code, report_name);
					status = "U";
					pwcReportParametersVO.setStatus(status);
					updatePwcGenerateReportHist(ctx, generateReportHistSeq, pwcReportParametersVO);
					this.getPwc_report_parametersService().updatePwc_report_parametersService(ctx,
							pwcReportParametersVO);
					throw new OurException(e.getMessage(), e);
				} finally {
					pwcReportParametersVO.setStatus(status);
					this.getPwc_report_parametersService().updatePwc_report_parametersService(ctx,
							pwcReportParametersVO);
				}
			}
		}

		generetedReportTaskVO.setGeneretedReportList(generetedReportList);
		generetedReportTaskVO.setFailedReportList(failedReportSet);

		return generetedReportTaskVO;
	}

	synchronized public List<GeneretedReportVO> runReportService(ServiceContext ctx, ReportingVO reportingVO)
			throws Exception {

		LOG.info("runReportService ...");
		generetedReportList = new ArrayList<GeneretedReportVO>();
		pwc_generated_report_file_col = new LinkedList<Pwc_generated_report_fileVO>();
		bankCode = reportingVO.getBank_code();
		saveToServer = reportingVO.isSaveToServer();
		user = reportingVO.getUser();
		String language = reportingVO.getLanguage();
		screenReportType = reportingVO.getReportType();
		reportFormat = reportingVO.getFormat();
		executionSource = reportingVO.getExecution_source();
		Long generateReportHistSeq = createReportHist(ctx, reportingVO);

		for (Pwc_report_parametersVO pwcReportParametersVO : reportingVO.getPwc_report_parametersVOList()) {
			try {

				printPwcReportParametersStatus(ctx, pwcReportParametersVO);
				report_code = pwcReportParametersVO.getReport_code();
				updatePwcReportParametersVO(ctx, pwcReportParametersVO);
				prepareParametersMap(pwcReportParametersVO);
				executeBeforeReport(ctx, pwcReportParametersVO, report_code, language);
				executeReport(ctx, generateReportHistSeq, pwcReportParametersVO);
				status = "S";
				pwcReportParametersVO.setStatus(status);
				updatePwcGenerateReportHist(ctx, generateReportHistSeq, pwcReportParametersVO);
				this.getPwc_report_parametersService().updatePwc_report_parametersService(ctx, pwcReportParametersVO);
				printPwcReportParametersStatus(ctx, pwcReportParametersVO);
			} catch (Exception e) {
				status = "U";
				pwcReportParametersVO.setStatus(status);
				updatePwcGenerateReportHist(ctx, generateReportHistSeq, pwcReportParametersVO);
				this.getPwc_report_parametersService().updatePwc_report_parametersService(ctx, pwcReportParametersVO);
				printPwcReportParametersStatus(ctx, pwcReportParametersVO);
				throw new OurException(e.getMessage(), e);
			} finally {
				pwcReportParametersVO.setStatus(status);
				this.getPwc_report_parametersService().updatePwc_report_parametersService(ctx, pwcReportParametersVO);
			}
		}

		return generetedReportList;
	}

	private void printPwcReportParametersStatus(ServiceContext ctx, Pwc_report_parametersVO pwcReportParametersVO)
			throws Exception {

		Pwc_report_parametersVO searchReportParamVO = new Pwc_report_parametersVO();
		searchReportParamVO.setReport_code(pwcReportParametersVO.getReport_code());
		searchReportParamVO.setReport_module(pwcReportParametersVO.getReport_module());
		List<Pwc_report_parametersVO> resultReportParamVOList = this.getPwc_report_parametersService()
				.searchPwc_report_parametersService(ctx, searchReportParamVO);
		if (resultReportParamVOList.size() > 0) {
			LOG.info("Report " + resultReportParamVOList.get(0).getReport_code() + "  Status : "
					+ resultReportParamVOList.get(0).getStatus());
		}

	}

	private void updatePwcReportParametersVO(ServiceContext ctx, Pwc_report_parametersVO pwcReportParametersVO)
			throws Exception {
		LOG.info("updatePwcReportParametersVO... :");

		Pwc_report_parametersVO searchReportParamVO = new Pwc_report_parametersVO();
		searchReportParamVO.setReport_code(pwcReportParametersVO.getReport_code());
		searchReportParamVO.setReport_module(pwcReportParametersVO.getReport_module());
		List<Pwc_report_parametersVO> resultReportParamVOList = this.getPwc_report_parametersService()
				.searchPwc_report_parametersService(ctx, searchReportParamVO);
		pwcReportParametersVO.setReport_name(resultReportParamVOList.get(0).getReport_name());
		pwcReportParametersVO.setReport_file_name(resultReportParamVOList.get(0).getReport_file_name());
		pwcReportParametersVO.setStatus(resultReportParamVOList.get(0).getStatus());
		pwcReportParametersVO.setSupport_pdf(resultReportParamVOList.get(0).getSupport_pdf());
		pwcReportParametersVO.setSupport_csv(resultReportParamVOList.get(0).getSupport_csv());
		pwcReportParametersVO.setTable_report(resultReportParamVOList.get(0).getTable_report());
		pwcReportParametersVO.setFields_report(resultReportParamVOList.get(0).getFields_report());
		pwcReportParametersVO.setParameter_v1_type(resultReportParamVOList.get(0).getParameter_v1_type());
		pwcReportParametersVO.setParameter_v2_type(resultReportParamVOList.get(0).getParameter_v2_type());
		pwcReportParametersVO.setParameter_v3_type(resultReportParamVOList.get(0).getParameter_v3_type());
		pwcReportParametersVO.setParameter_v4_type(resultReportParamVOList.get(0).getParameter_v4_type());
		pwcReportParametersVO.setParameter_v5_type(resultReportParamVOList.get(0).getParameter_v5_type());
		pwcReportParametersVO.setParameter_v6_type(resultReportParamVOList.get(0).getParameter_v6_type());
		pwcReportParametersVO.setParameter_v7_type(resultReportParamVOList.get(0).getParameter_v7_type());
		pwcReportParametersVO.setParameter_v8_type(resultReportParamVOList.get(0).getParameter_v8_type());
		pwcReportParametersVO.setParameter_v9_type(resultReportParamVOList.get(0).getParameter_v9_type());
		pwcReportParametersVO.setParameter_v10_type(resultReportParamVOList.get(0).getParameter_v10_type());
		pwcReportParametersVO.setParameter_v11_type(resultReportParamVOList.get(0).getParameter_v11_type());
		pwcReportParametersVO.setParameter_v12_type(resultReportParamVOList.get(0).getParameter_v12_type());

	}

	private void updatePwcGenerateReportHist(ServiceContext ctx, Long generateReportHistSeq,
			Pwc_report_parametersVO pwcReportParametersVO) throws Exception {
		LOG.info("updatePwcGenerateReportHist... :");

		Pwc_generate_report_histVO pwc_generate_report_histVO = new Pwc_generate_report_histVO();
		pwc_generate_report_histVO.setSequence_id(generateReportHistSeq);
		pwc_generate_report_histVO.setPage(1);
		pwc_generate_report_histVO.setPageSize(10);

		List<Pwc_generate_report_histVO> generate_report_histVOList = this.getPwc_generate_report_histService()
				.searchPwc_generate_report_histService(ctx, pwc_generate_report_histVO);
		if (generate_report_histVOList.size() > 0) {
			pwc_generate_report_histVO = generate_report_histVOList.get(0);
			preparePwc_generate_report_histVO(pwcReportParametersVO, pwc_generate_report_histVO);
			this.getPwc_generate_report_histService().updatePwc_generate_report_histService(ctx,
					pwc_generate_report_histVO);
		}
	}

	private void executeBeforeReport(ServiceContext ctx, Pwc_report_parametersVO pwcReportParametersVO,
			String report_code, String language) throws Exception {
		LOG.info("ReportingServiceImpl.executeBeforeReport... :");

		printPwcReportParametersStatus(ctx, pwcReportParametersVO);

		Execute_report_functionInVO executeReportFunctionInVOSearch = new Execute_report_functionInVO();
		executeReportFunctionInVOSearch.setP_bank_code(bankCode);
		executeReportFunctionInVOSearch.setP_report_code(report_code);
		executeReportFunctionInVOSearch.setP_langue(getReportLanguage(language));
		executeReportFunctionInVOSearch.setP_report_param_rec(prepareReport_param_recVO(pwcReportParametersVO));

		Execute_report_functionOutVO execute_report_functionOutVO = this.getPcrd_flex_before_reportService()
				.execute_report_function(ctx, executeReportFunctionInVOSearch);

	}

	/*
	 * private String prepareQuery(String reportBank, Pwc_report_parametersVO
	 * pwcReportParametersVO) throws Exception { String tableReport =
	 * pwcReportParametersVO.getTable_report(); String fieldsReport =
	 * pwcReportParametersVO.getFields_report();
	 * 
	 * 
	 * pwc_generate_report_histVO.setParameter_v1_data(pwcReportParametersVO());
	 * pwc_generate_report_histVO.setParameter_v1_label(pwcReportParametersVO.
	 * getParameter_v1_label()); parameter_v1_type
	 * 
	 * StringBuffer query = new
	 * StringBuffer("select count(*) as total from "+tableReport +
	 * " where bank_code ='"+reportBank+"'"); query.append(arg0);
	 * 
	 * 
	 * return query; }
	 */

	private void executeReport(ServiceContext ctx, Long generateReportHistSeq,
			Pwc_report_parametersVO pwcReportParametersVO) throws Exception {
		try {
			printPwcReportParametersStatus(ctx, pwcReportParametersVO);
			Pwc_bank_report_parametersVO pwc_bank_report_parametersVOSearch = new Pwc_bank_report_parametersVO();

			if (!bankCode.equals("ALL")) {
				pwc_bank_report_parametersVOSearch.setBank_code(bankCode);
			}
			pwc_bank_report_parametersVOSearch.setReport_code(report_code);
			List<Pwc_bank_report_parametersVO> pwc_bank_report_parametersList = this
					.getPwc_bank_report_parametersService()
					.searchPwc_bank_report_parametersService(ctx, pwc_bank_report_parametersVOSearch);
			
			if(pwc_bank_report_parametersList.size() == 0) {
				LOG.info("Missing parameters in table pwc_bank_report_parameters, report_code : " + report_code);
				throw new OurException("1039", new Exception(""));
			}
			String language1 = "";
			String language2 = "";
			String language3 = "";
			String language4 = "";
			String reportBank = "";
			String summaryGenarationFlag = "";
			String detailGenarationFlag = "";
			params.put("P_REPORT_NAME", pwcReportParametersVO.getReport_name());
			params.put("P_REPORT_FORMAT", reportFormat);
			String tableReport = pwcReportParametersVO.getTable_report();
			String fieldsReport = pwcReportParametersVO.getFields_report();

			for (Pwc_bank_report_parametersVO pwc_bank_report_parametersVO : pwc_bank_report_parametersList) {
				arrayLanguage[0] = pwc_bank_report_parametersVO.getLanguage_1();
				arrayLanguage[1] = pwc_bank_report_parametersVO.getLanguage_2();
				arrayLanguage[2] = pwc_bank_report_parametersVO.getLanguage_3();
				arrayLanguage[3] = pwc_bank_report_parametersVO.getLanguage_4();

				reportBank = pwc_bank_report_parametersVO.getBank_code();
				summaryGenarationFlag = pwc_bank_report_parametersVO.getSummary_genaration_flag();
				detailGenarationFlag = pwc_bank_report_parametersVO.getDetail_genaration_flag();

				reportType = screenReportType != null ? screenReportType
						: getReportType(detailGenarationFlag, summaryGenarationFlag);
				params.put("P_BANK_CODE", reportBank);
				params.put("P_REPORT_TYPE", reportType);

				String noDataFlag = null;
				if (tableReport != null && !tableReport.isEmpty() && tableReport.matches("[_a-zA-Z0-9\\.]+") ) {
					Connection conn = null;
					try {
						conn = getConn();

						// Statement stmt = conn.createStatement();
						// ResultSet rs = stmt.executeQuery("select count(*) as total from "+tableReport
						// + " where bank_code ='"+reportBank+"'");

						PreparedStatement ps = null;
						ResultSet rs = null;
						tableReport = StringEscapeUtils.escapeSql(tableReport);
						String query = "select count(*) as total from " + tableReport + " where bank_code = ?";

						ps = conn.prepareStatement(query);

						//ps.setString(1, tableReport.toString());
						ps.setString(1, reportBank);
						rs = ps.executeQuery();

						while (rs.next()) {
							noDataFlag = rs.getInt("total") > 0 ? "N" : "Y";
						}

					} catch (Exception e) {
						StringWriter sw = new StringWriter();
						PrintWriter pw = new PrintWriter(sw);
						e.printStackTrace(pw);
						LOG.error("executeReport Exception ... : " + e.getMessage());
						throw new OurException("1038", e);
					} finally {
						if (conn != null)
							try {
								if (!conn.isClosed())
									conn.close();
							} catch (SQLException e) {
								StringWriter sw = new StringWriter();
								PrintWriter pw = new PrintWriter(sw);
								e.printStackTrace(pw);
								LOG.error("executeReport Exception ... : " + e.getMessage());
								throw new OurException("1038", e);
							}
					}
				}

				params.put("P_NO_DATA_FLAG", noDataFlag);

				if (reportFormat.equals("PDF")) {
					for (int i = 0; i < arrayLanguage.length; i++) {
						if (arrayLanguage[i] != null && !arrayLanguage[i].equals("")) {
							params.put("P_LANGUAGE", arrayLanguage[i]);
							try {
								doBatchPrint(generateReportHistSeq);
							} catch (Exception e) {
								LOG.error("Loop Language executeReport Exception ... : " + e.getMessage());
								LOG.error("Loop Language executeReport caused Exception ... : " + e.getCause());
								throw new OurException("1038", e);
							}
						}

					}
				} else if (reportFormat.equals("CSV")) {
					if (arrayLanguage[0] != null && !arrayLanguage[0].equals("")) {
						params.put("P_LANGUAGE", arrayLanguage[0]);
						try {
							doCSV(generateReportHistSeq, pwcReportParametersVO);
						} catch (Exception e) {
							LOG.error("Loop Language executeReport Exception ... : " + e.getMessage());
							LOG.error("Loop Language executeReport caused Exception ... : " + e.getCause());
							throw new OurException("1038", e);
						}
					}
				} else if (reportFormat.equals("EE")) {
					try {
						for (int i = 0; i < arrayLanguage.length; i++) {
							if (arrayLanguage[i] != null && !arrayLanguage[i].equals("")) {
								params.put("P_LANGUAGE", arrayLanguage[i]);
								try {
									doBatchPrint(generateReportHistSeq);
								} catch (Exception e) {
									LOG.error("Loop Language executeReport Exception ... : " + e.getMessage());
									LOG.error("Loop Language executeReport caused Exception ... : " + e.getCause());
									throw new OurException("1038", e);
								}
							}

						}

						if (arrayLanguage[0] != null && !arrayLanguage[0].equals("")) {
							params.put("P_LANGUAGE", arrayLanguage[0]);
							try {
								doCSV(generateReportHistSeq, pwcReportParametersVO);
							} catch (Exception e) {
								LOG.error("Loop Language executeReport Exception ... : " + e.getMessage());
								LOG.error("Loop Language executeReport caused Exception ... : " + e.getCause());
								throw new OurException(e.getMessage(), e);
							}
						}
					} catch (Exception e) {
						LOG.error("Loop Language executeReport Exception ... : " + e.getMessage());
						LOG.error("Loop Language executeReport caused Exception ... : " + e.getCause());
						throw new OurException(e.getMessage(), e);
					}
				}
			}

			printPwcReportParametersStatus(ctx, pwcReportParametersVO);
		} catch (Exception e) {
			prepareFailedReportList(report_code, pwcReportParametersVO.getReport_name());
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			LOG.error("executeReport Exception ... : " + e.getMessage());
			LOG.error("executeReport caused Exception ... : " + e.getCause());
			throw new OurException(e.getMessage(), e);
		}
	}

	private String getReportType(String detailGenarationFlag, String summaryGenarationFlag) {
		LOG.info("detailGenarationFlag ... : " + detailGenarationFlag);
		LOG.info("summaryGenarationFlag ... : " + summaryGenarationFlag);

		String reportType = "B";
		boolean displayDetail = detailGenarationFlag == null
				|| (detailGenarationFlag != null && detailGenarationFlag.equalsIgnoreCase("Y"));
		boolean displaySummary = summaryGenarationFlag == null
				|| (summaryGenarationFlag != null && summaryGenarationFlag.equalsIgnoreCase("Y"));
		LOG.info("displayDetail ... : " + displayDetail);
		LOG.info("displaySummary ... : " + displaySummary);
		if (displayDetail && displaySummary) {
			return reportType;
		}
		if (displayDetail && !displaySummary) {
			reportType = "D";
		}

		if (!displayDetail && displaySummary) {
			reportType = "S";
		}

		return reportType;
	}

	public Map<String, Object> prepareParametersMap(Pwc_report_parametersVO pwcReportParametersVO) throws Exception {
		LOG.info("prepareParametersMap ...");
		params = new HashMap<String, Object>();
		params.put(pwcReportParametersVO.getParameter_v1_label(), pwcReportParametersVO.getParameter_v1_data());
		params.put(pwcReportParametersVO.getParameter_v2_label(), pwcReportParametersVO.getParameter_v2_data());
		params.put(pwcReportParametersVO.getParameter_v3_label(), pwcReportParametersVO.getParameter_v3_data());
		params.put(pwcReportParametersVO.getParameter_v4_label(), pwcReportParametersVO.getParameter_v4_data());
		params.put(pwcReportParametersVO.getParameter_v5_label(), pwcReportParametersVO.getParameter_v5_data());
		params.put(pwcReportParametersVO.getParameter_v6_label(), pwcReportParametersVO.getParameter_v6_data());
		params.put(pwcReportParametersVO.getParameter_v7_label(), pwcReportParametersVO.getParameter_v7_data());
		params.put(pwcReportParametersVO.getParameter_v8_label(), pwcReportParametersVO.getParameter_v8_data());
		params.put(pwcReportParametersVO.getParameter_v9_label(), pwcReportParametersVO.getParameter_v9_data());
		params.put(pwcReportParametersVO.getParameter_v10_label(), pwcReportParametersVO.getParameter_v10_data());
		params.put(pwcReportParametersVO.getParameter_v11_label(), pwcReportParametersVO.getParameter_v11_data());
		params.put(pwcReportParametersVO.getParameter_v12_label(), pwcReportParametersVO.getParameter_v12_data());
		params.put("P_REPORT_CODE", report_code);
		params.put("P_REPORT_MODULE", pwcReportParametersVO.getReport_module());
		params.put("P_USER", user);
		params.put("JOB_NAME", report_code);
		params.put("P_REPORT_FILE_NAME", pwcReportParametersVO.getReport_file_name());
		params.put("P_FILE_NAME_TO_GENERATED", pwcReportParametersVO.getGenerated_file_name());
		return params;
	}

	public Boolean doesReportExistService(ServiceContext ctx, String genereted_filename, String report_location)
			throws Exception {
		try {

			String filePath = report_location + genereted_filename;
			File in = new File(filePath);
			return in.exists();

		} catch (Exception e) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			LOG.error("doPdf Exception ... : " + e.getMessage());
			LOG.error("doPdf caused Exception ... : " + e.getCause());
		}

		return false;
	}

	public String createReportService(ServiceContext ctx, ReportingVO reportingVO) throws Exception {

		HashMap<String, Object> params = reportingVO.getParams();
		reportingVO.setParams(params);
		String langue = (String) params.get("P_LANGUE");
		String[] languageCountry = langue.split("_");

		params.put("REPORT_RESOURCE_BUNDLE", prepareBundles(ctx, reportingVO));
		params.put("SUBREPORT_DIR", this.jasper_path);

		LOG.info("EXPORT REPORT ...");
		for (Entry<String, Object> entry : params.entrySet()) {
			String cle = entry.getKey();
			Object valeur = entry.getValue();
			LOG.info(cle + " : " + valeur);
		}

		if (reportingVO.getFormat().equalsIgnoreCase("pdf")) {
			params.put("REPORT_LOCALE", new Locale("en", "US"));
			params.put("P_LANGUE", "en_US");
			doPdf(reportingVO.getSourceFileName(), reportingVO.getDestFileName(), params);
			LOG.info("END EXPORT REPORT ...");
			return "0000";
		}
		if (reportingVO.getFormat().equalsIgnoreCase("xls")) {
			doExcel(reportingVO.getSourceFileName(), reportingVO.getDestFileName(), reportingVO.getParams());
			LOG.info("END EXPORT REPORT ...");
			return "0000";
		}
		if (reportingVO.getFormat().equalsIgnoreCase("txt")) {
			doTxt(reportingVO.getSourceFileName(), reportingVO.getDestFileName(), reportingVO.getParams());
			LOG.info("END EXPORT REPORT ...");
			return "0000";
		}

		throw new OurException("0022", new Exception("format du report est invalide"));

	}

	/*
	 * private Locale getReportLocale(String pLangue) { String[] parts =
	 * pLangue.split("_"); return Locale.Locale(parts[0],parts[1]); }
	 */

	private void doPdf(String sourceFileName, String destFileName, Map<String, Object> params)
			throws JRException, NamingException, SQLException {
		try {
			Connection conn = getConn();
			String langue = (String) params.get("P_LANGUE");
			String bankCode = (String) params.get("P_BANK_CODE");
			DateFormat formatter = new SimpleDateFormat("ddMMyyyy");
			destFileName = destFileName + "_" + formatter.format(new java.util.Date());
			JasperRunManager.runReportToPdfFile(this.jasper_path + "/" + sourceFileName + ".jasper",
					this.generated_pdf_path + "/" + destFileName + ".pdf", params, conn);

			if (conn != null)
				if (!conn.isClosed())
					conn.close();

		} catch (JRException e) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			LOG.error("doPdf JRException ... : " + e.getMessage());
			LOG.error("doPdf JRException caused  ... : " + e.getCause());
		} catch (Exception e) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			LOG.error("doPdf Exception ... : " + e.getMessage());
			LOG.error("doPdf caused Exception ... : " + e.getCause());
		}

	}

	private void doExcel(String sourceFileName, String destFileName, Map<String, Object> map)
			throws NamingException, SQLException, JRException, OurException {
		try {
			JasperReport jreport;
			Connection conn = getConn();

			jreport = (JasperReport) JRLoader.loadObject(new FileInputStream(this.jasper_path + "/" + sourceFileName + ".jasper"));
			JasperPrint print;
			print = JasperFillManager.fillReport(jreport, map, conn);

			JRXlsExporter exporterXLS = initJRXlsExporter(print, destFileName);

			exporterXLS.exportReport();

			if (conn != null)
				if (!conn.isClosed())
					conn.close();

		} catch (Exception e) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			LOG.error("doExcel Exception ... : " + e.getMessage());
			LOG.error("doExcel caused Exception ... : " + e.getCause());
			throw new OurException(e.getMessage(), e);
		}

	}

	private void doCSV(Long generateReportHistSeq, Pwc_report_parametersVO pwcReportParametersVO)
			throws NamingException, SQLException, JRException, OurException {
		Connection conn = null;
		try {
			String reportDir = this.jasper_path + "/";
			String generatedCsvDir = saveToServer ? this.generated_csv_path + "/"
					: this.generated_screen_csv_path + "/";

			String jasperDetails = reportDir + (String) params.get("P_REPORT_FILE_NAME") + ".jasper";
			String jasperSummary = reportDir + (String) params.get("P_REPORT_FILE_NAME") + "_summary.jasper";
			String jasperCsv = reportDir + (String) params.get("P_REPORT_FILE_NAME") + "_view.jasper";
			String language = (String) params.get("P_LANGUAGE");
			String reportCode = (String) params.get("P_REPORT_CODE");
			String reportName = (String) params.get("P_REPORT_NAME");
			String reportModule = (String) params.get("P_REPORT_MODULE");
			String bankCode = (String) params.get("P_BANK_CODE");
			String noDataFlag = (String) params.get("P_NO_DATA_FLAG");

			for (Entry<String, Object> entry : params.entrySet()) {
				String cle = entry.getKey();
				Object valeur = entry.getValue();
				LOG.info(cle + " : " + valeur);
			}

			String reportFileName = getCSVReportName(reportCode, bankCode);

			JasperReport jreport;
			conn = getConn();

			Map<String, Object> params = new HashMap<String, Object>();
			params.put("SUBREPORT_DIR", this.jasper_path);
			params.put("P_BANK_CODE", bankCode);
			params.put("P_LANGUAGE", language);
			params.put("P_REPORT_CODE", reportCode);
			params.put("REPORT_LOCALE", getReportLocal(language));
			params.put("P_PROCESSING_DATE", processingDateFormatter.format(new Date()));
			params.put(pwcReportParametersVO.getParameter_v1_label(), pwcReportParametersVO.getParameter_v1_data());
			params.put(pwcReportParametersVO.getParameter_v2_label(), pwcReportParametersVO.getParameter_v2_data());
			params.put(pwcReportParametersVO.getParameter_v3_label(), pwcReportParametersVO.getParameter_v3_data());
			params.put(pwcReportParametersVO.getParameter_v4_label(), pwcReportParametersVO.getParameter_v4_data());
			params.put(pwcReportParametersVO.getParameter_v5_label(), pwcReportParametersVO.getParameter_v5_data());
			params.put(pwcReportParametersVO.getParameter_v6_label(), pwcReportParametersVO.getParameter_v6_data());
			params.put(pwcReportParametersVO.getParameter_v7_label(), pwcReportParametersVO.getParameter_v7_data());
			params.put(pwcReportParametersVO.getParameter_v8_label(), pwcReportParametersVO.getParameter_v8_data());
			params.put(pwcReportParametersVO.getParameter_v9_label(), pwcReportParametersVO.getParameter_v9_data());
			params.put(pwcReportParametersVO.getParameter_v10_label(), pwcReportParametersVO.getParameter_v10_data());
			params.put(pwcReportParametersVO.getParameter_v11_label(), pwcReportParametersVO.getParameter_v11_data());
			params.put(pwcReportParametersVO.getParameter_v12_label(), pwcReportParametersVO.getParameter_v12_data());
			params.put(JRParameter.IS_IGNORE_PAGINATION, true);

			JasperPrint jasperPrint = JasperFillManager.fillReport(jasperCsv, params, conn);
			JRCsvExporter exporterCsv = initJRCSVExporter(jasperPrint, generatedCsvDir + reportFileName);
			exporterCsv.setParameter(JRCsvExporterParameter.CHARACTER_ENCODING, "UTF-8");
			if(params.get("P_SEPARATOR")!=null) {
				exporterCsv.setParameter(JRCsvExporterParameter.FIELD_DELIMITER,params.get("P_SEPARATOR"));
			}
			exporterCsv.exportReport();

			generetedReportVO = prepareGeneretedReportVO(bankCode, reportModule, reportCode, generatedCsvDir,
					reportFileName, generationDate);
			generetedReportList.add(generetedReportVO);
			preparePwc_generated_report_fileVO(generateReportHistSeq, reportCode, reportModule, language, bankCode, "D",
					reportFileName, generationDate, generatedCsvDir, generetedReportVO.getFileSize(), noDataFlag);

		} catch (Exception e) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			LOG.error("doCSV Exception ... : " + e.getMessage());
			LOG.error("doCSV caused Exception ... : " + e.getCause());
			throw new OurException(e.getMessage(), e);
		} finally {
			if (conn != null)
				try {
					if (!conn.isClosed())
						conn.close();
				} catch (SQLException e) {
					StringWriter sw = new StringWriter();
					PrintWriter pw = new PrintWriter(sw);
					e.printStackTrace(pw);
					LOG.error("doCSV Exception ... : " + e.getMessage());
					LOG.error("doCSV caused Exception ... : " + e.getCause());
					throw new OurException(e.getMessage(), e);
				}
		}

	}

	private static JRCsvExporter initJRCSVExporter(JasperPrint print, String OutPutFileName) {

		JRCsvExporter exporter = new JRCsvExporter();
		exporter.setParameter(JRCsvExporterParameter.JASPER_PRINT, print);
		exporter.setParameter(JRCsvExporterParameter.OUTPUT_FILE_NAME, OutPutFileName);

		return exporter;
	}

	private Connection getConn() throws NamingException, SQLException {

		InitialContext initContext = new InitialContext();
		LOG.info("DataSource : " + jpub_datasource);
		DataSource datasource = (DataSource) initContext.lookup(jpub_datasource);
		Connection conn = datasource.getConnection();
		return conn;

	}

	private JRXlsExporter initJRXlsExporter(JasperPrint print, String OutPutFileName) throws OurException {
		if (print == null) {
			throw new OurException("0022", new Exception("Print invalide ou innexistant "));
		}
		JRXlsExporter exporterXLS = new JRXlsExporter();

		exporterXLS.setParameter(JRXlsExporterParameter.JASPER_PRINT, print);
		exporterXLS.setParameter(JRXlsExporterParameter.OUTPUT_FILE_NAME,
				this.generated_excel_path + "/" + OutPutFileName + ".xls");
		exporterXLS.setParameter(JRXlsExporterParameter.IS_ONE_PAGE_PER_SHEET, Boolean.TRUE);
		exporterXLS.setParameter(JRXlsExporterParameter.IS_DETECT_CELL_TYPE, Boolean.TRUE);
		exporterXLS.setParameter(JRXlsExporterParameter.IS_WHITE_PAGE_BACKGROUND, Boolean.FALSE);
		exporterXLS.setParameter(JRXlsExporterParameter.IS_REMOVE_EMPTY_SPACE_BETWEEN_ROWS, Boolean.TRUE);

		return exporterXLS;
	}

	/**
	 * Param�trage de l'exporter du format Txt
	 * 
	 * @param print
	 * @param OutPutFileName
	 * @return
	 */
	private JRExporter initJRTxtExporter(JasperPrint print, String OutPutFileName) {
		if (print == null) {
			throw new IllegalArgumentException("Print invalide ou innexistant 'print : " + print + "'");
		}
		JRExporter exporter = new JRCsvExporter();

		exporter.setParameter(JRExporterParameter.JASPER_PRINT, print);
		exporter.setParameter(JRExporterParameter.OUTPUT_FILE_NAME,
				this.generated_excel_path + OutPutFileName + ".txt");

		return exporter;
	}

	/**
	 * G�n�rer du TXT
	 * 
	 * @param report
	 *            Nom du raport
	 * @param params
	 *            Les param�tres du raport
	 * @throws JRException
	 */
	private void doTxt(String sourceFileName, Map<String, Object> params) {

		try {

			JasperReport JasperReport = (JasperReport) JRLoader
					.loadObject(new FileInputStream(this.jasper_path + "/" + sourceFileName + ".jasper"));
			JasperPrint print;
			print = JasperFillManager.fillReport(JasperReport, params, getConn());

			JRExporter exporter = initJRTxtExporter(print, sourceFileName);

			exporter.exportReport();

		} catch (Exception e) {

			e.printStackTrace();

			throw new IllegalArgumentException("Report invalide ou innexistant 'report : " + sourceFileName + "'");
		}

	}

	/**
	 * G�n�rer Txt en sp�cifiant le nom du fichier destination
	 * 
	 * @param sourceFileName
	 *            Nom du raport
	 * @param destFileName
	 *            Nom du fichier destination
	 * @param params
	 *            Les param�tres du raport
	 */
	private void doTxt(String sourceFileName, String destFileName, Map<String, Object> params) {

		try {
			Connection conn = getConn();
			JasperReport JasperReport = (JasperReport) JRLoader
					.loadObject(new FileInputStream(this.jasper_path + "/" + sourceFileName + ".jasper"));
			JasperPrint print;
			print = JasperFillManager.fillReport(JasperReport, params, conn);

			JRExporter exporter = initJRTxtExporter(print, destFileName);

			exporter.exportReport();

			if (conn != null)
				if (!conn.isClosed())
					conn.close();

		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Report invalide ou innexistant 'report : " + sourceFileName + "'");
		}

	}

	/*
	 * 
	 * Printing Reports by batch
	 * 
	 * 
	 */

	private void doBatchPrint(Long generateReportHistSeq) throws IOException, Exception {

		Connection conn = null;
		try {
			String reportDir = this.jasper_path + "/";
			String generatedPdfDir = saveToServer ? this.generated_pdf_path + "/"
					: this.generated_screen_pdf_path + "/";

			String jasperDetails = reportDir + (String) params.get("P_REPORT_FILE_NAME") + ".jasper";
			String jasperSummary = reportDir + (String) params.get("P_REPORT_FILE_NAME") + "_summary.jasper";
			String language = (String) params.get("P_LANGUAGE");
			String reportCode = (String) params.get("P_REPORT_CODE");
			String reportName = (String) params.get("P_REPORT_NAME");
			String reportModule = (String) params.get("P_REPORT_MODULE");
			String bankCode = (String) params.get("P_BANK_CODE");
			String noDataFlag = (String) params.get("P_NO_DATA_FLAG");
			params.put("REPORT_LOCALE", getReportLocal(language));
			params.put("SUBREPORT_DIR", this.jasper_path);
			params.put("NEW_DAETE", new Date());
			params.put("P_PROCESSING_DATE", processingDateFormatter.format(new Date()));

			String report_time_zone = System.getProperty("REPORT_TIME_ZONE");
			System.out.println("REPORT_TIME_ZONE variable : " + report_time_zone);

			System.out.println("Current time zone : " + TimeZone.getDefault().getID());

			for (Entry<String, Object> entry : params.entrySet()) {
				String cle = entry.getKey();
				Object valeur = entry.getValue();
				LOG.info(cle + " : " + valeur);
			}

			conn = getConn();
			JasperPrint jasperPrint = null;

			String reportType = (String) params.get("P_REPORT_TYPE") != null ? (String) params.get("P_REPORT_TYPE")
					: "";
			String reportFileName = "";
			GeneretedReportVO generetedReportVO = null;
			try {
				if (reportType.equals("") || reportType.equalsIgnoreCase("B") || reportType.equalsIgnoreCase("D")) {
					System.out.println("PDFFF .." + jasperDetails);
					reportFileName = getReportName(reportCode, language, bankCode, false);
					params.put("P_FILE_NAME", reportFileName);
					jasperPrint = JasperFillManager.fillReport(jasperDetails, params, conn);
					JasperExportManager.exportReportToPdfFile(jasperPrint, generatedPdfDir + reportFileName);
					generetedReportVO = prepareGeneretedReportVO(bankCode, reportModule, reportCode, generatedPdfDir,
							reportFileName, generationDate);
					generetedReportList.add(generetedReportVO);
					preparePwc_generated_report_fileVO(generateReportHistSeq, reportCode, reportModule, language,
							bankCode, "D", reportFileName, generationDate, generatedPdfDir,
							generetedReportVO.getFileSize(), noDataFlag);
				}
			} catch (JRException e) {
				prepareFailedReportList(report_code, reportName);
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				//e.printStackTrace(pw);
				LOG.error("doBatchPrint Exception ... : " + e.getMessage());
				LOG.error("doBatchPrint caused Exception ... : " + e.getCause());
				throw new OurException(e.getMessage(), e);
			}

			try {
				if (reportType.equals("") || reportType.equalsIgnoreCase("B") || reportType.equalsIgnoreCase("S")) {
					System.out.println("PDFFF .." + jasperSummary);
					reportFileName = getReportName(reportCode, language, bankCode, true);
					params.put("P_FILE_NAME", reportFileName);
					jasperPrint = JasperFillManager.fillReport(jasperSummary, params, conn);
					JasperExportManager.exportReportToPdfFile(jasperPrint, generatedPdfDir + reportFileName);
					generetedReportVO = prepareGeneretedReportVO(bankCode, reportModule, reportCode, generatedPdfDir,
							reportFileName, generationDate);
					generetedReportList.add(generetedReportVO);
					preparePwc_generated_report_fileVO(generateReportHistSeq, reportCode, reportModule, language,
							bankCode, "S", reportFileName, generationDate, generatedPdfDir,
							generetedReportVO.getFileSize(), noDataFlag);
				}
			} catch (JRException e) {
				prepareFailedReportList(report_code, reportName);
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				//e.printStackTrace(pw);
				LOG.error("doBatchPrint Exception ... : " + e.getMessage());
				LOG.error("doBatchPrint caused Exception ... : " + e.getCause());
				throw new OurException(e.getMessage(), e);
			}

		} catch (Exception e) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			//e.printStackTrace(pw);
			LOG.error("doBatchPrint Exception ... : " + e.getMessage());
			LOG.error("doBatchPrint caused Exception ... : " + e.getCause());
			throw new OurException(e.getMessage(), e);
		} finally {
			if (conn != null)
				try {
					if (!conn.isClosed())
						conn.close();
				} catch (SQLException e) {
					StringWriter sw = new StringWriter();
					PrintWriter pw = new PrintWriter(sw);
					e.printStackTrace(pw);
					LOG.error("doBatchPrint Exception ... : " + e.getMessage());
					LOG.error("doBatchPrint caused Exception ... : " + e.getCause());
					throw new OurException(e.getMessage(), e);
				}
		}
	}

	/**
	 * return report lacal
	 * 
	 * @param pLanguage
	 */
	private Locale getReportLocal(String pLanguage) {
		String languageCountry1 = "en";
		String languageCountry2 = "US";

		if (pLanguage.equals("ENG")) {
			languageCountry1 = "en";
			languageCountry2 = "US";
		} else if (pLanguage.equals("ESP")) {
			languageCountry1 = "es";
			languageCountry2 = "ES";
		} else if (pLanguage.equals("FRE")) {
			languageCountry1 = "fr";
			languageCountry2 = "FR";

		}
		reportlocale = new Locale(languageCountry1, languageCountry2);
		return reportlocale;

	}

	/**
	 * return report language
	 * 
	 * @param pLanguage
	 */
	private String getReportLanguage(String pLanguage) {
		String reportLanguage = "en_US";

		if (pLanguage.equals("ENG")) {
			reportLanguage = "en_US";
		} else if (pLanguage.equals("ESP")) {
			reportLanguage = "es_ES";
		} else if (pLanguage.equals("FRE")) {
			reportLanguage = "fr_FR";
		}
		return reportLanguage;
	}

	/**
	 * return report name
	 * 
	 * @param reportCode
	 * @param bankCode
	 * @param isSummary
	 */
	private String getCSVReportName(String reportCode, String bankCode) {

		StringBuffer name;
		if (params.containsKey("P_FILE_NAME_TO_GENERATED") && params.get("P_FILE_NAME_TO_GENERATED") != null) {
			name = new StringBuffer(bankCode + "_" + (String) params.get("P_FILE_NAME_TO_GENERATED"));
		} else {
			name = new StringBuffer(bankCode + "_" + reportCode);
		}
		generationDate = new Date();
		name.append("_" + formatter.format(generationDate) + ".csv");
		return name.toString();

	}

	/**
	 * return report name
	 * 
	 * @param reportCode
	 * @param language
	 * @param bankCode
	 * @param isSummary
	 */
	private String getReportName(String reportCode, String language, String bankCode, boolean isSummary) {

		StringBuffer pdfFile;
		if (params.containsKey("P_FILE_NAME_TO_GENERATED") && params.get("P_FILE_NAME_TO_GENERATED") != null) {
			pdfFile = new StringBuffer(bankCode + "_" + language + "_" + (String) params.get("P_FILE_NAME_TO_GENERATED"));
		} else {
			pdfFile = new StringBuffer(bankCode + "_" + language + "_" + reportCode);
		}
		if (isSummary) {
			pdfFile.append("_summary");
		}
		generationDate = new Date();
		pdfFile.append("_" + formatter.format(generationDate) + ".pdf");
		return pdfFile.toString();

	}

	/**
	 * return GeneretedReportVO
	 *
	 * @param fileName
	 * @param creationDate
	 * @param size
	 */
	private GeneretedReportVO prepareGeneretedReportVO(String bankCode, String reportModule, String reportCode,
			String generatedPdfDir, String fileName, Date creationDate) {

		File pdf = new File(generatedPdfDir + fileName);
		// size in KO
		double size = pdf.length();

		if (size > 0 && size < 1024)
			size = 1;
		else if (size >= 1024)
			size = pdf.length() / 1024;

		generetedReportVO = new GeneretedReportVO();
		generetedReportVO.setBank_code(bankCode);
		generetedReportVO.setFileName(fileName);
		generetedReportVO.setCreationDate(formatter.format(creationDate));
		generetedReportVO.setFileSize(new BigDecimal(size));
		generetedReportVO.setSaveToServer(saveToServer);
		generetedReportVO.setReport_module(reportModule);
		generetedReportVO.setReport_code(reportCode);

		return generetedReportVO;
	}

	/**
	 * return Report_param_recVO
	 *
	 * @param pwcReportParametersVO
	 */
	private Report_param_recVO prepareReport_param_recVO(Pwc_report_parametersVO pwcReportParametersVO) {
		Report_param_recVO report_param_recVO = new Report_param_recVO();
		report_param_recVO.setParameter_v1_data(pwcReportParametersVO.getParameter_v1_data());
		report_param_recVO.setParameter_v2_data(pwcReportParametersVO.getParameter_v2_data());
		report_param_recVO.setParameter_v3_data(pwcReportParametersVO.getParameter_v3_data());
		report_param_recVO.setParameter_v4_data(pwcReportParametersVO.getParameter_v4_data());
		report_param_recVO.setParameter_v5_data(pwcReportParametersVO.getParameter_v5_data());
		report_param_recVO.setParameter_v6_data(pwcReportParametersVO.getParameter_v6_data());
		report_param_recVO.setParameter_v7_data(pwcReportParametersVO.getParameter_v7_data());
		report_param_recVO.setParameter_v8_data(pwcReportParametersVO.getParameter_v8_data());
		report_param_recVO.setParameter_v9_data(pwcReportParametersVO.getParameter_v9_data());
		report_param_recVO.setParameter_v10_data(pwcReportParametersVO.getParameter_v10_data());
		report_param_recVO.setParameter_v11_data(pwcReportParametersVO.getParameter_v11_data());
		report_param_recVO.setParameter_v12_data(pwcReportParametersVO.getParameter_v12_data());
		return report_param_recVO;
	}

	/**
	 * return Pwc_generate_report_histVO
	 *
	 * @param pwc_generate_report_histVO
	 */
	private Pwc_generate_report_histVO preparePwc_generate_report_histVO(Pwc_report_parametersVO pwcReportParametersVO,
			Pwc_generate_report_histVO pwc_generate_report_histVO) {

		pwc_generate_report_histVO.setReport_module(pwcReportParametersVO.getReport_module());
		pwc_generate_report_histVO.setReport_name(pwcReportParametersVO.getReport_name());
		pwc_generate_report_histVO.setReport_file_name(pwcReportParametersVO.getReport_file_name());
		pwc_generate_report_histVO.setReport_status(pwcReportParametersVO.getStatus());
		pwc_generate_report_histVO.setParameter_v1_data(pwcReportParametersVO.getParameter_v1_data());
		pwc_generate_report_histVO.setParameter_v1_label(pwcReportParametersVO.getParameter_v1_label());
		pwc_generate_report_histVO.setParameter_v1_type(pwcReportParametersVO.getParameter_v1_type());
		pwc_generate_report_histVO.setParameter_v2_data(pwcReportParametersVO.getParameter_v2_data());
		pwc_generate_report_histVO.setParameter_v2_label(pwcReportParametersVO.getParameter_v2_label());
		pwc_generate_report_histVO.setParameter_v2_type(pwcReportParametersVO.getParameter_v2_type());
		pwc_generate_report_histVO.setParameter_v3_data(pwcReportParametersVO.getParameter_v3_data());
		pwc_generate_report_histVO.setParameter_v3_label(pwcReportParametersVO.getParameter_v3_label());
		pwc_generate_report_histVO.setParameter_v3_type(pwcReportParametersVO.getParameter_v3_type());
		pwc_generate_report_histVO.setParameter_v4_data(pwcReportParametersVO.getParameter_v4_data());
		pwc_generate_report_histVO.setParameter_v4_label(pwcReportParametersVO.getParameter_v4_label());
		pwc_generate_report_histVO.setParameter_v4_type(pwcReportParametersVO.getParameter_v4_type());
		pwc_generate_report_histVO.setParameter_v5_data(pwcReportParametersVO.getParameter_v5_data());
		pwc_generate_report_histVO.setParameter_v5_label(pwcReportParametersVO.getParameter_v5_label());
		pwc_generate_report_histVO.setParameter_v5_type(pwcReportParametersVO.getParameter_v5_type());
		pwc_generate_report_histVO.setParameter_v6_data(pwcReportParametersVO.getParameter_v6_data());
		pwc_generate_report_histVO.setParameter_v6_label(pwcReportParametersVO.getParameter_v6_label());
		pwc_generate_report_histVO.setParameter_v6_type(pwcReportParametersVO.getParameter_v6_type());
		pwc_generate_report_histVO.setParameter_v7_data(pwcReportParametersVO.getParameter_v7_data());
		pwc_generate_report_histVO.setParameter_v7_label(pwcReportParametersVO.getParameter_v7_label());
		pwc_generate_report_histVO.setParameter_v7_type(pwcReportParametersVO.getParameter_v7_type());
		pwc_generate_report_histVO.setParameter_v8_data(pwcReportParametersVO.getParameter_v8_data());
		pwc_generate_report_histVO.setParameter_v8_label(pwcReportParametersVO.getParameter_v8_label());
		pwc_generate_report_histVO.setParameter_v8_type(pwcReportParametersVO.getParameter_v8_type());
		pwc_generate_report_histVO.setParameter_v9_data(pwcReportParametersVO.getParameter_v9_data());
		pwc_generate_report_histVO.setParameter_v9_label(pwcReportParametersVO.getParameter_v9_label());
		pwc_generate_report_histVO.setParameter_v9_type(pwcReportParametersVO.getParameter_v9_type());
		pwc_generate_report_histVO.setParameter_v10_data(pwcReportParametersVO.getParameter_v10_data());
		pwc_generate_report_histVO.setParameter_v10_label(pwcReportParametersVO.getParameter_v10_label());
		pwc_generate_report_histVO.setParameter_v10_type(pwcReportParametersVO.getParameter_v10_type());
		pwc_generate_report_histVO.setParameter_v11_data(pwcReportParametersVO.getParameter_v11_data());
		pwc_generate_report_histVO.setParameter_v11_label(pwcReportParametersVO.getParameter_v11_label());
		pwc_generate_report_histVO.setParameter_v11_type(pwcReportParametersVO.getParameter_v11_type());
		pwc_generate_report_histVO.setParameter_v12_data(pwcReportParametersVO.getParameter_v12_data());
		pwc_generate_report_histVO.setParameter_v12_label(pwcReportParametersVO.getParameter_v12_label());
		pwc_generate_report_histVO.setParameter_v12_type(pwcReportParametersVO.getParameter_v12_type());
		pwc_generate_report_histVO.setExecution_source(executionSource);
		pwc_generate_report_histVO.setReport_type(executionSource.equals("S") ? screenReportType : "B");
		pwc_generate_report_histVO.setReport_format(reportFormat);
		pwc_generate_report_histVO.setSave_to_server(saveToServer ? "Y" : "N");

		pwc_generate_report_histVO.setPwc_generated_report_file_col(pwc_generated_report_file_col);
		return pwc_generate_report_histVO;
	}

	/**
	 * return Pwc_generated_report_fileVO
	 *
	 * @param sequenceId
	 * @param reportCode
	 * @param reportModule,
	 * @param language
	 * @param bankCode
	 * @param report_type
	 * @param fileName,
	 * @param generationDate
	 * @param reportLocation
	 * @param size
	 */
	private void preparePwc_generated_report_fileVO(Long sequenceId, String reportCode, String reportModule,
			String language, String bankCode, String report_type, String fileName, Date generationDate,
			String reportLocation, BigDecimal size, String noDataFlag) {
		Pwc_generated_report_fileVO pwc_generated_report_fileVO = new Pwc_generated_report_fileVO();
		pwc_generated_report_fileVO.setSequence_id(sequenceId);
		pwc_generated_report_fileVO.setReport_code(reportCode);
		pwc_generated_report_fileVO.setReport_module(reportModule);
		pwc_generated_report_fileVO.setLanguage(language);
		pwc_generated_report_fileVO.setReport_bank_code(bankCode);
		pwc_generated_report_fileVO.setReport_type(report_type);
		pwc_generated_report_fileVO.setReport_format(reportFormat);
		pwc_generated_report_fileVO.setGenereted_filename(fileName);
		pwc_generated_report_fileVO.setGeneration_date(generationDate);
		pwc_generated_report_fileVO.setReport_location(reportLocation);
		pwc_generated_report_fileVO.setReport_size(size);
		pwc_generated_report_fileVO.setNo_data_flag(noDataFlag);
		pwc_generated_report_file_col.add(pwc_generated_report_fileVO);

	}

	private Long createPwcGenerateReportHist(ServiceContext ctx, Pwc_report_parametersVO pwcReportParametersVO,
			ReportingVO reportingVO) throws Exception {
		LOG.info("createPwcGenerateReportHist... :");

		Pwc_generate_report_histVO pwc_generate_report_histVO = new Pwc_generate_report_histVO();
		pwc_generate_report_histVO.setTask_name(reportingVO.getTask_name());
		pwc_generate_report_histVO.setReport_code(pwcReportParametersVO.getReport_code());
		pwc_generate_report_histVO.setReport_module(pwcReportParametersVO.getReport_module());
		pwc_generate_report_histVO.setExecution_business_date(reportingVO.getBusiness_date());
		pwc_generate_report_histVO.setUser_bank_code(reportingVO.getBank_code());
		pwc_generate_report_histVO.setUser_requestor(reportingVO.getUser_id());
		pwc_generate_report_histVO.setUser_profile(reportingVO.getUser_profile_id());
		pwc_generate_report_histVO.setReport_status("I");
		pwc_generate_report_histVO.setExecution_source("B");
		pwc_generate_report_histVO.setSave_to_server("Y");
		pwc_generate_report_histVO.setDate_create(new Date());

		this.getPwc_generate_report_histService().createPwc_generate_report_histService(ctx,
				pwc_generate_report_histVO);
		return pwc_generate_report_histVO.getSequence_id();

	}

	private void prepareFailedReportList(String report_code, String report_name) {
		failedReportVO = new FailedReportVO();
		failedReportVO.setReport_code(report_code);
		failedReportVO.setReport_name(report_name);
		failedReportSet.add(failedReportVO);
	}
	
	private Long createReportHist(ServiceContext ctx, 
			ReportingVO reportingVO) throws Exception {
		LOG.info("createReportHist... :");

		Pwc_report_parametersVO pwc_report_parametersVO = reportingVO.getPwc_report_parametersVOList().iterator()
				.next();

		Pwc_generate_report_histVO pwc_generate_report_histVO = new Pwc_generate_report_histVO();

		pwc_generate_report_histVO.setExecution_source(reportingVO.getExecution_source());
		pwc_generate_report_histVO.setReport_code(pwc_report_parametersVO.getReport_code());
		pwc_generate_report_histVO.setReport_format(reportingVO.getFormat());
		pwc_generate_report_histVO.setReport_module(pwc_report_parametersVO.getReport_module());
		pwc_generate_report_histVO.setReport_name(pwc_report_parametersVO.getReport_name());
		pwc_generate_report_histVO.setReport_status("I");
		pwc_generate_report_histVO.setReport_type(reportingVO.getReportType());
		pwc_generate_report_histVO.setSave_to_server(reportingVO.isSaveToServer() ? "Y" : "N");
		pwc_generate_report_histVO.setUser_requestor(reportingVO.getUser());
		
		pwc_generate_report_histVO.setParameter_v1_type(pwc_report_parametersVO.getParameter_v1_type());
		pwc_generate_report_histVO.setParameter_v1_label(pwc_report_parametersVO.getParameter_v1_label());
		pwc_generate_report_histVO.setParameter_v1_data(pwc_report_parametersVO.getParameter_v1_data());

		pwc_generate_report_histVO.setParameter_v2_type(pwc_report_parametersVO.getParameter_v2_type());
		pwc_generate_report_histVO.setParameter_v2_label(pwc_report_parametersVO.getParameter_v2_label());
		pwc_generate_report_histVO.setParameter_v2_data(pwc_report_parametersVO.getParameter_v2_data());

		pwc_generate_report_histVO.setParameter_v3_type(pwc_report_parametersVO.getParameter_v3_type());
		pwc_generate_report_histVO.setParameter_v3_label(pwc_report_parametersVO.getParameter_v3_label());
		pwc_generate_report_histVO.setParameter_v3_data(pwc_report_parametersVO.getParameter_v3_data());

		pwc_generate_report_histVO.setParameter_v4_type(pwc_report_parametersVO.getParameter_v4_type());
		pwc_generate_report_histVO.setParameter_v4_label(pwc_report_parametersVO.getParameter_v4_label());
		pwc_generate_report_histVO.setParameter_v4_data(pwc_report_parametersVO.getParameter_v4_data());

		pwc_generate_report_histVO.setParameter_v5_type(pwc_report_parametersVO.getParameter_v5_type());
		pwc_generate_report_histVO.setParameter_v5_label(pwc_report_parametersVO.getParameter_v5_label());
		pwc_generate_report_histVO.setParameter_v5_data(pwc_report_parametersVO.getParameter_v5_data());

		pwc_generate_report_histVO.setParameter_v6_type(pwc_report_parametersVO.getParameter_v6_type());
		pwc_generate_report_histVO.setParameter_v6_label(pwc_report_parametersVO.getParameter_v6_label());
		pwc_generate_report_histVO.setParameter_v6_data(pwc_report_parametersVO.getParameter_v6_data());

		pwc_generate_report_histVO.setParameter_v7_type(pwc_report_parametersVO.getParameter_v7_type());
		pwc_generate_report_histVO.setParameter_v7_label(pwc_report_parametersVO.getParameter_v7_label());
		pwc_generate_report_histVO.setParameter_v7_data(pwc_report_parametersVO.getParameter_v7_data());

		pwc_generate_report_histVO.setParameter_v8_type(pwc_report_parametersVO.getParameter_v8_type());
		pwc_generate_report_histVO.setParameter_v8_label(pwc_report_parametersVO.getParameter_v8_label());
		pwc_generate_report_histVO.setParameter_v8_data(pwc_report_parametersVO.getParameter_v8_data());

		pwc_generate_report_histVO.setParameter_v9_type(pwc_report_parametersVO.getParameter_v9_type());
		pwc_generate_report_histVO.setParameter_v9_label(pwc_report_parametersVO.getParameter_v9_label());
		pwc_generate_report_histVO.setParameter_v9_data(pwc_report_parametersVO.getParameter_v9_data());

		pwc_generate_report_histVO.setParameter_v10_type(pwc_report_parametersVO.getParameter_v10_type());
		pwc_generate_report_histVO.setParameter_v10_label(pwc_report_parametersVO.getParameter_v10_label());
		pwc_generate_report_histVO.setParameter_v10_data(pwc_report_parametersVO.getParameter_v10_data());

		pwc_generate_report_histVO.setParameter_v11_type(pwc_report_parametersVO.getParameter_v11_type());
		pwc_generate_report_histVO.setParameter_v11_label(pwc_report_parametersVO.getParameter_v11_label());
		pwc_generate_report_histVO.setParameter_v11_data(pwc_report_parametersVO.getParameter_v11_data());

		pwc_generate_report_histVO.setParameter_v12_type(pwc_report_parametersVO.getParameter_v12_type());
		pwc_generate_report_histVO.setParameter_v12_label(pwc_report_parametersVO.getParameter_v12_label());
		pwc_generate_report_histVO.setParameter_v12_data(pwc_report_parametersVO.getParameter_v12_data());

		pwc_generate_report_histVO.setUser_bank_code(reportingVO.getBank_code());
		pwc_generate_report_histVO.setExecution_business_date(reportingVO.getBusiness_date());
		pwc_generate_report_histVO.setUser_profile(reportingVO.getUser_profile_id());

		this.getPwc_generate_report_histService().createPwc_generate_report_histService(ctx,
				pwc_generate_report_histVO);
		return pwc_generate_report_histVO.getSequence_id();

	}

	public String getFilePath(ServiceContext ctx, String fileName, boolean savedToServer) {
		String fullLocation = "";
		String type = fileName.substring(fileName.length() - 3).toUpperCase();
		if (savedToServer) {
			if (type.equalsIgnoreCase("PDF")) {
				fullLocation = this.generated_pdf_path + "/" + fileName;
			} else {
				if (type.equalsIgnoreCase("CSV")) {
					fullLocation = this.generated_csv_path + "/" + fileName;
				}
			}
		} else {
			if (type.equalsIgnoreCase("PDF")) {
				fullLocation = this.generated_screen_pdf_path + "/" + fileName;
			} else {
				if (type.equalsIgnoreCase("CSV")) {
					fullLocation = this.generated_screen_csv_path + "/" + fileName;
				}
			}
		}
		
		return fullLocation;
	}

}
