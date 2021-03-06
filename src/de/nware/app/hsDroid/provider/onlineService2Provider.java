package de.nware.app.hsDroid.provider;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.cookie.BrowserCompatSpec;
import org.apache.http.impl.cookie.CookieSpecBase;
import org.xml.sax.SAXException;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;
import android.util.Xml;
import de.nware.app.hsDroid.R;
import de.nware.app.hsDroid.data.Exam;
import de.nware.app.hsDroid.data.ExamInfo;
import de.nware.app.hsDroid.data.StaticSessionData;
import de.nware.app.hsDroid.provider.onlineService2Data.CertificationsCol;
import de.nware.app.hsDroid.provider.onlineService2Data.ExamInfos;
import de.nware.app.hsDroid.provider.onlineService2Data.ExamsCol;
import de.nware.app.hsDroid.provider.onlineService2Data.ExamsUpdateCol;

/**
 *  This file is part of hsDroid.
 * 
 *  hsDroid is an Android App for students to view their grades from QIS Online Service 
 *  Copyright (C) 2011,2012  Oliver Eichner <n0izeland@gmail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *  
 *  hsDroid is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  
 *  
 *  Diese Datei ist Teil von hsDroid.
 *  
 *  hsDroid ist Freie Software: Sie können es unter den Bedingungen
 *  der GNU General Public License, wie von der Free Software Foundation,
 *  Version 3 der Lizenz oder jeder späteren veröffentlichten Version, 
 *  weiterverbreiten und/oder modifizieren.
 *  
 *  hsDroid wird in der Hoffnung, dass es nützlich sein wird, aber
 *  OHNE JEDE GEWÄHRLEISTUNG, bereitgestellt; sogar ohne die implizite
 *  Gewährleistung der MARKTFÄHIGKEIT oder EIGNUNG FÜR EINEN BESTIMMTEN ZWECK.
 *  Siehe die GNU General Public License für weitere Details.
 *  
 *  Sie sollten eine Kopie der GNU General Public License zusammen mit diesem
 *  Programm erhalten haben. Wenn nicht, siehe <http://www.gnu.org/licenses/>.
 */

// TODO: Auto-generated Javadoc

/**
 * {@link ContentProvider} für QIS Server (Online Service 2)
 * 
 * @author oli
 * 
 */
public class onlineService2Provider extends ContentProvider {

	/** Debug TAG. */
	private static final String TAG = "OnlineServiceContentProvider";

	/** Der Datenbankname. */
	private static final String DATABASE_NAME = "hsdroid.db";

	/** Die Datenbankversion. */
	private static final int VERSION = 3;

	/** Die AUTHORITY. */
	public static final String AUTHORITY = "de.nware.app.hsDroid.provider.onlineService2Provider";

	private static final UriMatcher mUriMatcher;

	/** Die Konstante EXAMS. */
	private static final int EXAMS = 1;

	/** Die Konstante EXAMS_UPDATE. */
	private static final int EXAMS_UPDATE = 2;

	/** Die Konstante CERTIFICATIONS. */
	private static final int CERTIFICATIONS = 3;

	/** Die Konstante EXAMINFOS. */
	private static final int EXAMINFOS = 4;

	/** Projection map. */
	private static HashMap<String, String> examsProjectionMap;

	/** Die Konstante CERTIFICATIONS_COLUMNS. */
	private static final String[] CERTIFICATIONS_COLUMNS = new String[] {
			BaseColumns._ID, CertificationsCol.TITLE, CertificationsCol.LINK };

	/** Die Konstante EXAMS_UPDATE_COLUMNS. */
	private static final String[] EXAMS_UPDATE_COLUMNS = new String[] {
			BaseColumns._ID, ExamsUpdateCol.AMOUNT, ExamsUpdateCol.NEWEXAMS };

	/** Die Konstante EXAM_INFOS_COLUMNS. */
	private static final String[] EXAM_INFOS_COLUMNS = new String[] {
			BaseColumns._ID, ExamInfos.SEHRGUT, ExamInfos.GUT,
			ExamInfos.BEFRIEDIGEND, ExamInfos.AUSREICHEND,
			ExamInfos.NICHTAUSREICHEND, ExamInfos.ATTENDEES, ExamInfos.AVERAGE };

	// HTTP gedöns
	/** Die QIS Url. */
	final String urlBase = "https://qis2.hs-karlsruhe.de/qisserver/rds";

	/** Link für Bescheinigungen. */
	// final String certificationURLTmpl =
	// "%s?state=qissosreports&besch=%s&next=wait.vm&asi=%s";
	final String certificationURLTmpl2 = "%s?state=verpublish&vmfile=no&moduleCall=Report&publishSubDir=qissosreports&publishConfFile=%s";
	/** Bescheinigungstypen. */
	// final String[] certificationType = { "stammdaten", "studbesch",
	// "studbeschengl", "bafoeg", "kvv", "studienzeit" };

	final String[] certificationType2 = { "stammdaten", "studienbescheinigung",
			"studienbescheinigungenglisch", "bafoegbescheinigung",
			"kvvbescheinigung", "studienzeitbescheinigung" };
	/** Bescheinigungsname. */
	final String[] certificationName = { "Datenkontrollblatt",
			"Immatrikulationsbescheinigung",
			"Englische Immatrikulationsbescheinigung",
			"Bescheinigung nach § 9 BAföG", "KVV-Bescheinigung",
			"Studienzeitbescheinigung" };

	/** Die Konstante USER_AGENT. */
	private static final String USER_AGENT = TAG + "/" + VERSION;

	/** Temporärer Buffer zum halten der HTTP Get Antwort. */
	private static byte[] mContentBuffer = new byte[2048];

	/** Der http client. */
	// private HttpClient mHttpClient = null;// = new DefaultHttpClient();

	// Timeout variablen
	// private final int connectionTimeoutMillis = 3000;
	// private final int socketTimeoutMillis = 3000;

	private SharedPreferences mPreferences;

	// DB
	/**
	 * Der/Die/Das Class DatabaseHelper.
	 */
	private class DatabaseHelper extends SQLiteOpenHelper {

		/**
		 * Instanziiert ein/e neue/s database helper.
		 * 
		 * @param context
		 *            der/die/das context
		 */

		private String tablename;
		private SharedPreferences mPreferences;

		public DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, VERSION);
			mPreferences = PreferenceManager
					.getDefaultSharedPreferences(context);

			tablename = onlineService2Data.EXAMS_TABLE_NAME;
			// tablename = username;
			// Log.d(TAG, "username:[" + username + "]");
		}

		public String getTableName() {
			return tablename;
		}

		public void updateDBUser() {
			String username = mPreferences.getString("UserSave", "");
			Log.d(TAG, "dbUser update: " + username);
			SharedPreferences.Editor editor = mPreferences.edit();
			editor.putString("dbUser", username);
			editor.commit();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * android.database.sqlite.SQLiteOpenHelper#onCreate(android.database
		 * .sqlite.SQLiteDatabase)
		 */
		@Override
		public void onCreate(SQLiteDatabase db) {
			// db.setLocale(Locale.getDefault());
			// db.setLockingEnabled(true);
			// db.setVersion(VERSION);

			Log.d(TAG, "create table");

			db.execSQL("CREATE TABLE " + tablename + " (" + BaseColumns._ID
					+ " INTEGER PRIMARY KEY AUTOINCREMENT," + ExamsCol.SEMESTER
					+ " VARCHAR(255)," + ExamsCol.PASSED + " INTEGER,"
					+ ExamsCol.EXAMNAME + " VARCHAR(255)," + ExamsCol.EXAMNR
					+ " VARCHAR(255)," + ExamsCol.EXAMDATE + " VARCHAR(255),"
					+ ExamsCol.ADMITTED + " VARCHAR(255)," + ExamsCol.NOTATION
					+ " VARCHAR(255)," + ExamsCol.ATTEMPTS + " VARCHAR(255),"
					+ ExamsCol.GRADE + " VARCHAR(255)," + ExamsCol.LINKID
					+ " INTEGER," + ExamsCol.STUDIENGANG + " VARCHAR(255)"
					+ ");");
			Log.d(TAG, "create table done");
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * android.database.sqlite.SQLiteOpenHelper#onUpgrade(android.database
		 * .sqlite.SQLiteDatabase, int, int)
		 */
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.w(TAG, "Datenbank Upgrade von Version " + oldVersion + " zu "
					+ newVersion + ". Alle alten Daten gehen verloren");
			db.execSQL("DROP TABLE IF EXISTS " + tablename);
			onCreate(db);

		}

	}

	/** Der/Die/Das m open helper. */
	private DatabaseHelper mOpenHelper;

	static {
		mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		mUriMatcher.addURI(AUTHORITY, "exams", EXAMS);
		mUriMatcher.addURI(AUTHORITY, onlineService2Data.CERTIFICATIONS_NAME,
				CERTIFICATIONS);
		mUriMatcher.addURI(AUTHORITY, onlineService2Data.EXAMS_UPDATE_NAME,
				EXAMS_UPDATE);
		mUriMatcher.addURI(AUTHORITY, onlineService2Data.EXAM_INFOS_NAME,
				EXAMINFOS);

		examsProjectionMap = new HashMap<String, String>();
		examsProjectionMap.put(BaseColumns._ID, BaseColumns._ID);
		examsProjectionMap.put(onlineService2Data.ExamsCol.SEMESTER,
				onlineService2Data.ExamsCol.SEMESTER);
		examsProjectionMap.put(onlineService2Data.ExamsCol.PASSED,
				onlineService2Data.ExamsCol.PASSED);
		examsProjectionMap.put(onlineService2Data.ExamsCol.EXAMNAME,
				onlineService2Data.ExamsCol.EXAMNAME);
		examsProjectionMap.put(onlineService2Data.ExamsCol.EXAMNR,
				onlineService2Data.ExamsCol.EXAMNR);
		examsProjectionMap.put(onlineService2Data.ExamsCol.EXAMDATE,
				onlineService2Data.ExamsCol.EXAMDATE);
		examsProjectionMap.put(onlineService2Data.ExamsCol.ADMITTED,
				onlineService2Data.ExamsCol.ADMITTED);
		examsProjectionMap.put(onlineService2Data.ExamsCol.NOTATION,
				onlineService2Data.ExamsCol.NOTATION);
		examsProjectionMap.put(onlineService2Data.ExamsCol.ATTEMPTS,
				onlineService2Data.ExamsCol.ATTEMPTS);
		examsProjectionMap.put(onlineService2Data.ExamsCol.GRADE,
				onlineService2Data.ExamsCol.GRADE);
		examsProjectionMap.put(onlineService2Data.ExamsCol.LINKID,
				onlineService2Data.ExamsCol.LINKID);
		examsProjectionMap.put(onlineService2Data.ExamsCol.STUDIENGANG,
				onlineService2Data.ExamsCol.STUDIENGANG);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.content.ContentProvider#delete(android.net.Uri,
	 * java.lang.String, java.lang.String[])
	 */
	@Override
	public int delete(Uri uri, String whereClause, String[] whereArgs) {
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		int count;
		switch (mUriMatcher.match(uri)) {
		case EXAMS:
			count = db.delete(mOpenHelper.getTableName(), whereClause,
					whereArgs);
			break;

		default:
			throw new IllegalArgumentException("Unbekannte URI " + uri);
		}

		return count;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.content.ContentProvider#getType(android.net.Uri)
	 */
	@Override
	public String getType(Uri uri) {
		switch (mUriMatcher.match(uri)) {
		case EXAMS:
			return ExamsCol.CONTENT_TYPE;
		case EXAMINFOS:
			return ExamInfos.CONTENT_TYPE;
		case EXAMS_UPDATE:
			return ExamsUpdateCol.CONTENT_TYPE;
		case CERTIFICATIONS:
			return CertificationsCol.CONTENT_TYPE;
		default:
			throw new IllegalArgumentException("Unbekannte URI " + uri);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.content.ContentProvider#insert(android.net.Uri,
	 * android.content.ContentValues)
	 */
	@Override
	public Uri insert(Uri uri, ContentValues initialValues) {
		if (mUriMatcher.match(uri) != EXAMS) {
			throw new IllegalArgumentException("Unbekannte URI " + uri);
		}

		ContentValues contentValues;
		if (initialValues != null) {
			contentValues = new ContentValues(initialValues);
		} else {
			contentValues = new ContentValues();
		}
		if (mOpenHelper == null) {
			Log.d(TAG, "mOpenHelper NULL");
		}
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		long rowID = db.insert(mOpenHelper.getTableName(), ExamsCol.EXAMNAME,
				contentValues);
		if (rowID > 0) {
			Uri examsUri = ContentUris.withAppendedId(ExamsCol.CONTENT_URI,
					rowID);
			getContext().getContentResolver().notifyChange(examsUri, null); // Observer?
			return examsUri;
		}
		throw new SQLException("Konnte row nicht zu " + uri + " hinzufügen");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.content.ContentProvider#onCreate()
	 */
	@Override
	public boolean onCreate() {
		mOpenHelper = new DatabaseHelper(getContext());
		mPreferences = PreferenceManager
				.getDefaultSharedPreferences(getContext());
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.content.ContentProvider#query(android.net.Uri,
	 * java.lang.String[], java.lang.String, java.lang.String[],
	 * java.lang.String)
	 */
	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {

		Cursor cursor = null;
		switch (mUriMatcher.match(uri)) {
		case EXAMS:
			SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
			qb.setTables(mOpenHelper.getTableName());
			qb.setProjectionMap(examsProjectionMap);
			SQLiteDatabase db = mOpenHelper.getReadableDatabase();
			try {
				cursor = qb.query(db, projection, selection, selectionArgs,
						null, null, sortOrder);
			} catch (SQLException e) {
				e.printStackTrace();
				Log.d(TAG, "SqlError: " + e.getMessage());
			}
			cursor.setNotificationUri(getContext().getContentResolver(), uri);
			break;
		case EXAMS_UPDATE:
			MatrixCursor cur = new MatrixCursor(EXAMS_UPDATE_COLUMNS);
			Integer[] columnValues = updateGrades();
			cur.addRow(new Object[] { 0, columnValues[0], columnValues[1] });
			return cur;
		case EXAMINFOS:
			cursor = getExamInfos(selectionArgs[0], selectionArgs[1], false);
			break;
		case CERTIFICATIONS:
			cursor = getCertifications();
			break;
		default:
			throw new IllegalArgumentException("Unbekannte URI " + uri);
		}

		return cursor;
	}

	/**
	 * Gibt exam infos.
	 * 
	 * @param infoID
	 *            der/die/das info id
	 * @return Der/Die/das exam infos
	 */
	private Cursor getExamInfos(String infoID, String studiengang,
			boolean isSecondTry) {
		// Log.d(TAG, "infoID:" + infoID + " asi:" + StaticSessionData.asiKey);
		// String studiengang = "IB";
		final String examInfoURL = urlBase
				+ "?state=notenspiegelStudent&next=list.vm&nextdir=qispos/notenspiegel/student&createInfos=Y&struct=abschluss&nodeID=auswahlBaum%7Cabschluss%3Aabschl%3D"
				+ mPreferences.getString("degreePref", "58")
				+ "%2Cstgnr%3D1%7Cstudiengang%3Astg%3D" + studiengang
				+ "%7CpruefungOnTop%3Alabnr%3D" + infoID + "&expand=0&asi="
				+ StaticSessionData.asiKey;

		String response = getResponse(examInfoURL);

		BufferedReader rd = new BufferedReader(new StringReader(response));

		// XXX Workaround für denn Fall, dass der Notenspiegel noch nicht
		// geladen wurde. Seite Laden, da sonst die Notenübwesicht nicht
		// funktioniert..
		try {
			String line;
			Boolean record = false;
			StringBuilder sb = new StringBuilder();
			boolean checkNextLineForTD = false;
			while ((line = rd.readLine()) != null) {

				if (!record
						&& line.contains("<table border=\"0\" align=\"left\"  width=\"60%\">")) {
					record = true;
				}
				if (record && line.contains("</table>")) {
					line = line.replaceAll("&nbsp;", "");
					line.trim();
					sb.append(line);
					// System.out.println("last line: " + line);
					record = false;
					break;
				}
				if (record) {
					// alle nicht anzeigbaren zeichen entfernen (\n,\t,\s...)
					line = line.trim();

					// alle html leerzeichen müssen raus, da der xml reader nix
					// mit anfangen kann
					line = line.replaceAll("&nbsp;", "");

					// da die <img ..> tags nicht xml like "well formed" sind,
					// muss man sie ein bissel anpassen ;)
					if (line.contains("<img")) {
						// Log.d("examInfo parser", line);
						line = line.substring(0, line.indexOf(">") + 1)
								+ "</a>";
					}

					// XXX workaround für die verkorxte notenverteilung..
					// fehlende </td>s einfügen
					if (checkNextLineForTD) {
						// System.out.println("linecheck: [" + line + "]");
						if (line.contains("</tr>")) {

							line = "</td>" + line;
							// System.out.println("linechecked: [" + line +
							// "]");
						}
						checkNextLineForTD = false;
					}
					if (line.contains("<td class=\"tabelle1\" valign=\"top\" align=\"right\">")) {
						if (!line.contains("</td>")) {
							checkNextLineForTD = true;
						}
					}

					sb.append(line);
					// System.out.println("line: " + line);
				}
			}
			rd.close();
			String htmlContentString = sb.toString();
			Cursor returnCursor = null;
			try {
				returnCursor = parseExamInfo(htmlContentString);
			} catch (Exception e) {
				Log.d(TAG, "examInfoParser Error: " + e.getMessage());
				final String notenSpiegelURLTmpl = urlBase
						+ "?state=notenspiegelStudent&next=list.vm&nextdir=qispos/notenspiegel/student&createInfos=Y&struct=studiengang&nodeID=auswahlBaum%7Cabschluss%3Aabschl%3D"
						+ mPreferences.getString("degreePref", "58")
						+ "%2Cstgnr%3D1&expand=1&asi="
						+ StaticSessionData.asiKey
						+ "#auswahlBaum%7Cabschluss%3Aabschl%3D"
						+ mPreferences.getString("degreePref", "58")
						+ "%2Cstgnr%3D1";

				getResponse(notenSpiegelURLTmpl);
				if (!isSecondTry) {
					return getExamInfos(infoID, studiengang, true);
				}

			}

			return returnCursor;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			Log.d(TAG, e.getMessage());
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Parses the exam info.
	 * 
	 * @param htmlContentString
	 *            der/die/das html content string
	 * @return der/die/das cursor
	 * @throws SAXException
	 */
	private Cursor parseExamInfo(String htmlContentString) throws SAXException {
		final MatrixCursor cursor = new MatrixCursor(EXAM_INFOS_COLUMNS);
		try {
			ExamInfoParser handler = new ExamInfoParser();
			System.out.println("content exam info: " + htmlContentString);
			Xml.parse(htmlContentString, handler);
			ExamInfo exInfos = handler.getExamInfos();
			cursor.addRow(new Object[] { 0, exInfos.getSehrGutAmount(),
					exInfos.getGutAmount(), exInfos.getBefriedigendAmount(),
					exInfos.getAusreichendAmount(),
					exInfos.getNichtAusreichendAmount(),
					exInfos.getAttendees(), exInfos.getAverage() });
		} catch (SAXException e) {
			Log.e("read:SAXException:", e.getMessage());
			e.printStackTrace();
			throw new SAXException(e);
		}
		return cursor;
	}

	/**
	 * Gibt certifications.
	 * 
	 * @return Der/Die/das certifications
	 */
	private Cursor getCertifications() {

		final MatrixCursor cursor = new MatrixCursor(CERTIFICATIONS_COLUMNS);
		int count = 0;
		for (String certType : certificationType2) {
			String downloadUrl = String.format(certificationURLTmpl2, urlBase,
					certType, StaticSessionData.asiKey);
			cursor.addRow(new Object[] { count, certificationName[count],
					downloadUrl });
			count++;
		}

		return cursor;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.content.ContentProvider#update(android.net.Uri,
	 * android.content.ContentValues, java.lang.String, java.lang.String[])
	 */
	@Override
	public int update(Uri uri, ContentValues values, String whereClause,
			String[] whereArgs) {
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		int count;
		switch (mUriMatcher.match(uri)) {
		case EXAMS:
			count = db.update(mOpenHelper.getTableName(), values, whereClause,
					whereArgs);
			break;

		default:
			throw new IllegalArgumentException("Unbekannte URI " + uri);
		}
		getContext().getContentResolver().notifyChange(uri, null); // Observer?
		return count;
	}

	/**
	 * Stellt HTTP Anfrage und liefert deren Antwort zurück.
	 * 
	 * @param url
	 *            die formatierte URL
	 * @return die HTML/XML Antwort
	 * @throws Exception
	 */
	private synchronized String getResponse(String url) {

		// Log.d(TAG, "URL: " + url);
		final HttpPost httpPost = new HttpPost(url);
		httpPost.addHeader("User-Agent", USER_AGENT);
		CookieSpecBase cookieSpecBase = new BrowserCompatSpec();
		List<Header> cookieHeader = cookieSpecBase
				.formatCookies(StaticSessionData.cookies);
		httpPost.setHeader(cookieHeader.get(0));

		// FIXME geht nicht als int Preference, wegen
		// preferenceScreen/editText...
		int connectionTimeoutMillis = Integer
				.valueOf(StaticSessionData.sPreferences.getString(getContext()
						.getString(R.string.Preference_ConnectionTimeout),
						"1500"));
		HttpClient client = HttpClientFactory
				.getHttpClient(connectionTimeoutMillis);
		try {

			final HttpResponse response = client.execute(httpPost);

			// Prüfen ob HTTP Antwort ok ist.
			final StatusLine status = response.getStatusLine();

			if (status.getStatusCode() != HttpStatus.SC_OK) {
				Log.d(TAG, "http status code: " + status.getStatusCode());
				throw new RuntimeException("Ungültige Antwort vom Server: "
						+ status.toString());
			}

			// Hole Content Stream
			final HttpEntity entity = response.getEntity();

			// content.
			final InputStream inputStream = entity.getContent();
			final ByteArrayOutputStream content = new ByteArrayOutputStream();

			// response lesen in ByteArrayOutputStream.
			int readBytes = 0;
			while ((readBytes = inputStream.read(mContentBuffer)) != -1) {
				content.write(mContentBuffer, 0, readBytes);
			}

			// Stream nach String
			String output = new String(content.toByteArray());

			// Stream freigeben
			content.close();
			return output;

		} catch (IOException e) {
			Log.d(TAG, e.getMessage());
			throw new RuntimeException("Verbindung fehlgeschlagen: "
					+ e.getMessage(), e);
		}

	}

	/**
	 * Prüfen ob eine bestimmte Prüfungsleistung schon eingetragen ist.
	 * 
	 * @param examnr
	 *            Prüfungsnummer
	 * @param examdate
	 *            Prüfungsdatum
	 * @return true, wenn erfolgreich
	 */
	public boolean examExists(String examnr, String examdate) {
		SQLiteDatabase mDb = mOpenHelper.getReadableDatabase();
		Cursor cursor = mDb.rawQuery(
				"select 1 from " + mOpenHelper.getTableName() + " where "
						+ onlineService2Data.ExamsCol.EXAMNR + "=? AND "
						+ onlineService2Data.ExamsCol.EXAMDATE + "=?",
				new String[] { examnr, examdate });
		boolean exists = (cursor.getCount() > 0);
		cursor.close();
		return exists;
	}

	/**
	 * Notenspiegel aktuallisieren
	 * 
	 * @return integer[] mit gesamt anzahl der Prüfungsleistungen und anzahl der
	 *         Neuen Prüfungen
	 */
	public Integer[] updateGrades() {

		mOpenHelper.updateDBUser();

		final String notenSpiegelURLTmpl = urlBase
				+ "?state=notenspiegelStudent&next=list.vm&nextdir=qispos/notenspiegel/student&createInfos=Y&struct=auswahlBaum&nodeID=auswahlBaum%7Cabschluss%3Aabschl%3D"
				+ mPreferences.getString("degreePref", "58")
				+ "%2Cstgnr%3D1&expand=1&asi=" + StaticSessionData.asiKey
				+ "#auswahlBaum%7Cabschluss%3Aabschl%3D"
				+ mPreferences.getString("degreePref", "58") + "%2Cstgnr%3D1";
		Log.d(TAG, "url: " + notenSpiegelURLTmpl);
		String response = getResponse(notenSpiegelURLTmpl);

		BufferedReader rd = new BufferedReader(new StringReader(response));

		try {
			String line;
			Boolean record = false;
			StringBuilder sb = new StringBuilder();
			while ((line = rd.readLine()) != null) {
				if (!record && line.contains("<table border=\"0\">")) {
					record = true;
				}
				if (record && line.contains("</table>")) {
					line = line.replaceAll("&nbsp;", "");
					line.trim();
					sb.append(line);
					// System.out.println("last line: " + line);
					record = false;
					break;
				}
				if (record) {
					// alle nicht anzeigbaren zeichen entfernen (\n,\t,\s...)
					line = line.trim();

					// alle html leerzeichen müssen raus, da der xml reader nix
					// mit anfangen kann
					line = line.replaceAll("&nbsp;", "");

					// da die <img ..> tags nicht xml like "well formed" sind,
					// muss man sie ein bissel anpassen ;)
					if (line.contains("<img")) {
						// Log.d("grade parser", line);
						line = line.substring(0, line.indexOf(">") + 1)
								+ "</a>";
					}
					sb.append(line);
					// System.out.println("line: " + line);
				}
			}
			rd.close();
			String htmlContentString = sb.toString();
			return read(htmlContentString);

		} catch (IOException e) {
			// TODO Auto-generated catch block
			Log.d(TAG, e.getMessage());
			e.printStackTrace();
		}
		return null;

	}

	/**
	 * Notenspiegel Lesen
	 * 
	 * @param htmlContent
	 *            html content
	 * @return integer[] mit gesamt anzahl der Prüfungsleistungen und anzahl der
	 *         Neuen Prüfungen
	 */
	private Integer[] read(String htmlContent) {
		Integer[] counter = { 0, 0 };
		try {

			ExamParser handler = new ExamParser();
			Xml.parse(htmlContent, handler);

			for (Exam iterable_element : handler.getExams()) {
				// Log.d(TAG, "update: lid: " + iterable_element.getInfoID());
				if (!examExists(iterable_element.getExamNr(),
						iterable_element.getExamDate())) {
					counter[1]++;
					// Log.d(TAG, "exam: insert " +
					// iterable_element.getExamName() + " into DB");
					ContentValues values = new ContentValues();
					values.put(onlineService2Data.ExamsCol.SEMESTER,
							iterable_element.getSemester());
					// values.put(onlineService2Data.ExamsCol.PASSED,
					// (iterable_element.isPassed() ? 1 : 0));
					values.put(onlineService2Data.ExamsCol.PASSED,
							iterable_element.isPassed());
					values.put(onlineService2Data.ExamsCol.EXAMNAME,
							iterable_element.getExamName());
					values.put(onlineService2Data.ExamsCol.EXAMNR,
							iterable_element.getExamNr());
					values.put(onlineService2Data.ExamsCol.EXAMDATE,
							iterable_element.getExamDate());
					values.put(onlineService2Data.ExamsCol.ADMITTED,
							iterable_element.getAdmitted());
					values.put(onlineService2Data.ExamsCol.NOTATION,
							iterable_element.getNotation());
					values.put(onlineService2Data.ExamsCol.ATTEMPTS,
							iterable_element.getAttempts());
					values.put(onlineService2Data.ExamsCol.GRADE,
							iterable_element.getGrade());
					values.put(onlineService2Data.ExamsCol.LINKID,
							iterable_element.getInfoID());
					values.put(onlineService2Data.ExamsCol.STUDIENGANG,
							iterable_element.getStudiengang());
					// Log.d(TAG, "insert..");
					insert(onlineService2Data.ExamsCol.CONTENT_URI, values);
				} else {
					// Log.d(TAG, "exam: " + iterable_element.getExamName() +
					// " already in DB");
				}
				counter[0]++;
			}
		} catch (SAXException e) {
			Log.e("read:SAXException:", e.getMessage());
			e.printStackTrace();
		}
		return counter;
	}
}
