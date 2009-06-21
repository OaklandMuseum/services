/*	
 * YearIDPartTest
 *
 * Test class for YearIDPart.
 *
 * Copyright 2009 Regents of the University of California
 *
 * Licensed under the Educational Community License (ECL), Version 2.0.
 * You may not use this file except in compliance with this License.
 *
 * You may obtain a copy of the ECL 2.0 License at
 * https://source.collectionspace.org/collection-space/LICENSE.txt
 *
 * @author $Author: aron $
 * @version $Revision: 267 $
 * $Date: 2009-06-19 19:03:38 -0700 (Fri, 19 Jun 2009) $
 */

package org.collectionspace.services.id;

import static org.junit.Assert.fail;
import java.util.Calendar;
import java.util.GregorianCalendar;
import junit.framework.TestCase;

public class YearIDPartTest extends TestCase {

	IDPart part;
	String year = "1999";

	public String getCurrentYear() {
		Calendar cal = GregorianCalendar.getInstance();
    int y = cal.get(Calendar.YEAR);
		return Integer.toString(y);
	}

	public void testCurrentID() {

		part = new YearIDPart();
		assertEquals(getCurrentYear(), part.getCurrentID());

		part = new YearIDPart(year);
		assertEquals(year, part.getCurrentID());

	}
	

/*
	public void testNextID() {
		part = new YearIDPart("XYZ");		
		assertEquals("XYZ", part.getNextID());			
	}

	public void testReset() {
	
		part = new YearIDPart(".");
		assertEquals(".", part.getNextID());
		part.reset();
		assertEquals(".", part.getNextID());
			
	}

	public void testInitialID() {
		part = new YearIDPart("-");
		assertEquals("-", part.getInitialID());
	}

*/

	public void testNullInitialValue() {
	
		try {
			part = new YearIDPart(null);
			fail("Should have thrown IllegalArgumentException here");
		} catch (IllegalArgumentException expected) {
			// This Exception should be thrown, and thus the test should pass.
		}
		
	}

	public void testEmptyInitialValue() {
	
		try {
			part = new YearIDPart("");
			fail("Should have thrown IllegalArgumentException here");
		} catch (IllegalArgumentException expected) {
			// This Exception should be thrown, and thus the test should pass.
		}

	}
	
	// @TODO: Add more tests of boundary conditions, exceptions ...
 
}
