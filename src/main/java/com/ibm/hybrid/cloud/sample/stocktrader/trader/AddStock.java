/*
       Copyright 2017-2019 IBM Corp All Rights Reserved

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package com.ibm.hybrid.cloud.sample.stocktrader.trader;

import com.ibm.hybrid.cloud.sample.stocktrader.trader.client.PortfolioClient;
import com.ibm.hybrid.cloud.sample.stocktrader.trader.json.Portfolio;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

//CDI 1.2
import javax.inject.Inject;
import javax.enterprise.context.RequestScoped;

//Servlet 3.1
import javax.servlet.ServletException;
import javax.servlet.annotation.HttpConstraint;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

//mpJWT 1.0
import org.eclipse.microprofile.jwt.JsonWebToken;
import com.ibm.websphere.security.openidconnect.PropagationHelper;

//mpRestClient 1.0
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Servlet implementation class AddStock
 */
@WebServlet(description = "Add Stock servlet", urlPatterns = { "/addStock" })
@ServletSecurity(@HttpConstraint(rolesAllowed = { "StockTrader" } ))
@RequestScoped
public class AddStock extends HttpServlet {
	private static final long serialVersionUID = 4815162342L;
	private static Logger logger = Logger.getLogger(AddStock.class.getName());

	private NumberFormat currency = null;

	private @Inject @RestClient PortfolioClient portfolioClient;

	private @Inject JsonWebToken jwt;

	/**
	 * @see HttpServlet#HttpServlet()
	 */
	public AddStock() {
		super();

		currency = NumberFormat.getNumberInstance();
		currency.setMinimumFractionDigits(2);
		currency.setMaximumFractionDigits(2);
		currency.setRoundingMode(RoundingMode.HALF_UP);
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String owner = request.getParameter("owner");

		String commission = getCommission(request, owner);

		Writer writer = response.getWriter();
		writer.append("<!DOCTYPE html>");
		writer.append("<html>");
		writer.append("  <head>");
		writer.append("    <title>Stock Trader</title>");
		writer.append("    <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">");
		writer.append("  </head>");
		writer.append("  <body>");
		writer.append("    <img src=\"header.jpg\" width=\"534\" height=\"200\"/>");
		writer.append("    <br/>");
		writer.append("    <br/>");
		writer.append("    <form method=\"post\"/>");
		writer.append("      <table>");
		writer.append("        <tr>");
		writer.append("          <td><b>Owner:</b></td>");
		writer.append("          <td>"+owner+"</td>");
		writer.append("        </tr>");
		writer.append("        <tr>");
		writer.append("          <td><b>Commission:</b></td>");
		writer.append("          <td>"+commission+"</td>");
		writer.append("        </tr>");
		writer.append("        <tr>");
		writer.append("          <td><b>Stock Symbol:</b></td>");
		writer.append("          <td><input type=\"text\" name=\"symbol\"></td>");
		writer.append("        </tr>");
		writer.append("        <tr>");
		writer.append("          <td><b>Number of Shares:</b></td>");
		writer.append("          <td><input type=\"text\" name=\"shares\"></td>");
		writer.append("        </tr>");
		writer.append("      </table>");
		writer.append("      <br/>");
		writer.append("      <input type=\"submit\" name=\"submit\" value=\"Submit\" style=\"font-family: sans-serif; font-size: 16px;\"/>");
		writer.append("    </form>");
		writer.append("    <br/>");
		writer.append("    <a href=\"https://github.com/slombardo2/\">");
		writer.append("      <img src=\"footer.jpg\"/>");
		writer.append("    </a>");
		writer.append("  </body>");
		writer.append("</html>");
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String owner = request.getParameter("owner");
		String symbol = request.getParameter("symbol");
		String shareString = request.getParameter("shares");

		if ((shareString!=null) && !shareString.equals("")) {
			int shares = Integer.parseInt(shareString);
			//PortfolioServices.updatePortfolio(request, owner, symbol, shares);
			portfolioClient.updatePortfolio("Bearer "+getJWT(), owner, symbol, shares);
		}

		response.sendRedirect("summary");
	}

	private String getCommission(HttpServletRequest request, String owner) {
		String formattedCommission = "<b>Free!</b>";
		try {
			logger.info("Getting commission");
			//JsonObject portfolio = PortfolioServices.getPortfolio(request, owner);
			Portfolio portfolio = portfolioClient.getPortfolio("Bearer "+getJWT(), owner);
			double commission = portfolio.getNextCommission();
			if (commission!=0.0) formattedCommission = "$"+currency.format(commission);
			logger.info("Got commission: "+formattedCommission);
		} catch (Throwable t) {
			logException(t);
		}
		return formattedCommission;
	}

	private void logException(Throwable t) {
		logger.warning(t.getClass().getName()+": "+t.getMessage());

		//only log the stack trace if the level has been set to at least FINE
		if (logger.isLoggable(Level.FINE)) {
			StringWriter writer = new StringWriter();
			t.printStackTrace(new PrintWriter(writer));
			logger.fine(writer.toString());
		}
	}

	private String getJWT() {
		String token;
		if("bearer".equals(PropagationHelper.getAccessTokenType())) {
			token = PropagationHelper.getIdToken().getAccessToken();
			logger.fine("Retrieved JWT provided through oidcClientConnect feature");
		} else {
			token = jwt.getRawToken();
			logger.fine("Retrieved JWT provided through CDI injected JsonWebToken");
		}
		return token;
	}
}
