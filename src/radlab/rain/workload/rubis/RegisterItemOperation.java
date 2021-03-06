/*
 * Copyright (c) 2010, Regents of the University of California
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 *  * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *  * Neither the name of the University of California, Berkeley
 * nor the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Author: Marco Guazzone (marco.guazzone@gmail.com), 2013.
 */

package radlab.rain.workload.rubis;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import radlab.rain.IScoreboard;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.NameValuePair;
import radlab.rain.workload.rubis.model.RubisItem;
import radlab.rain.workload.rubis.model.RubisUser;


/**
 * Register-Item operation.
 *
 * This is the operation of selling a certain item.
 *
 * Emulates the following requests:
 * 1. Fill-in the form and click on the 'Register item!' button
 *
 * @author Marco Guazzone (marco.guazzone@gmail.com)
 */
public class RegisterItemOperation extends RubisOperation 
{
	public RegisterItemOperation(boolean interactive, IScoreboard scoreboard) 
	{
		super(interactive, scoreboard);
		this._operationName = "Register-Item";
		this._operationIndex = RubisGenerator.REGISTER_ITEM_OP;
		//this._mustBeSync = true;
	}

	@Override
	public void execute() throws Throwable
	{
		StringBuilder response = null;

		// Need a logged user
		RubisUser loggedUser = this.getUtility().getUser(this.getSessionState().getLoggedUserId());
		if (!this.getUtility().isRegisteredUser(loggedUser))
		{
			this.getLogger().warning("Need a logged user; got an anonymous one. Operation interrupted.");
			this.setFailed(true);
			return;
		}

		HttpPost reqPost = null;
		List<NameValuePair> form = null;
		UrlEncodedFormEntity entity = null;

		// Generate a new item
		RubisItem item = this.getUtility().newItem(this.getSessionState().getLoggedUserId());
		if (!this.getUtility().isValidItem(item))
		{
			this.getLogger().warning("No valid item has been found. Operation interrupted.");
			this.setFailed(true);
			return;
		}

		// Fill-in the form and click on the 'Register item!' button
		reqPost = new HttpPost(this.getGenerator().getRegisterItemURL());
		form = new ArrayList<NameValuePair>();
		form.add(new BasicNameValuePair("name", item.name));
		form.add(new BasicNameValuePair("description", item.description));
		form.add(new BasicNameValuePair("initialPrice", Float.toString(item.initialPrice)));
		form.add(new BasicNameValuePair("reservePrice", Float.toString(item.reservePrice)));
		form.add(new BasicNameValuePair("buyNow", Float.toString(item.buyNow)));
		form.add(new BasicNameValuePair("duration", Integer.toString(this.getUtility().getDaysBetween(item.startDate, item.endDate))));
		form.add(new BasicNameValuePair("quantity", Integer.toString(item.quantity)));
		form.add(new BasicNameValuePair("userId", Integer.toString(loggedUser.id)));
		//form.add(new BasicNameValuePair("categoryId", Integer.toString(this.getGenerator().getCategory(item.category).id)));
		form.add(new BasicNameValuePair("categoryId", Integer.toString(item.category)));
		entity = new UrlEncodedFormEntity(form, "UTF-8");
		reqPost.setEntity(entity);
		response = this.getHttpTransport().fetch(reqPost);
		this.trace(reqPost.getURI().toString());
		if (!this.getGenerator().checkHttpResponse(response.toString()))
		{
			this.getLogger().severe("Problems in performing request to URL: " + reqPost.getURI() + " (HTTP status code: " + this.getHttpTransport().getStatusCode() + "). Server response: " + response);
			throw new IOException("Problems in performing request to URL: " + reqPost.getURI() + " (HTTP status code: " + this.getHttpTransport().getStatusCode() + ")");
		}

		// Save session data
		this.getSessionState().setLastResponse(response.toString());
		this.getSessionState().setItemId(item.id);

		this.setFailed(!this.getUtility().checkRubisResponse(response.toString()));
	}
}
