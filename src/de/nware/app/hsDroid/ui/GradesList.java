package de.nware.app.hsDroid.ui;

import static de.nware.app.hsDroid.data.StaticSessionData.sPreferences;

import java.util.Date;
import java.util.HashMap;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.BaseColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import de.nware.app.hsDroid.HsDroidMain;
import de.nware.app.hsDroid.R;
import de.nware.app.hsDroid.data.StaticSessionData;
import de.nware.app.hsDroid.provider.onlineService2Data.ExamInfos;
import de.nware.app.hsDroid.provider.onlineService2Data.ExamsCol;
import de.nware.app.hsDroid.provider.onlineService2Data.ExamsUpdateCol;

/**
 * {@link ListActivity} zum anzeigen der Prüfungen
 * 
 * @author Oliver Eichner
 * 
 */
public class GradesList extends nActivity {

	private static final String TAG = "GradesListActivity";
	private ListView lv;
	private Cursor cursor = null;
	private Cursor examinfoCursor = null;

	private ExamDBAdapter mExamAdapter;

	private ProgressDialog mProgressDialog = null;

	private ExamInfoThread mExamInfoThread;

	private boolean autoUpdate;
	private boolean forceAutoUpdate = false;

	private static final byte DIALOG_PROGRESS = 1;

	private final int HANDLER_MSG_REFRESH = 1;
	private final int HANDLER_MSG_ERROR = 99;
	private final int HANDLER_MSG_INFO_READY = 4;

	private static final String FILTER_ALL = "all";
	private static final String FILTER_ALL_FAILED = "allfail";
	private static final String FILTER_ALL_PASSED = "allpassed";
	private static final String FILTER_ACTUAL = "act";
	private static final String FILTER_ACTUAL_FAILED = "actfail";
	private static final String FILTER_ACTUAL_PASSED = "actpassed";
	private static String ACTUAL_FILTER = FILTER_ALL;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		checkSession();
		setContentView(R.layout.grade_list_view);
		customTitle(getText(R.string.grades_view).toString());

		// einstellungne holen
		StaticSessionData.reloadSharedPrefs(this);

		// Datenbank und User überprüfen
		String dbUser = sPreferences.getString("dbUser", "0");
		String user = sPreferences.getString("UserSave", "");
		Log.d(TAG, "dbUser:" + dbUser + " user:" + user);
		if (!dbUser.equals(user)) {
			showTitleProgress();
			showToast(getString(R.string.text_initialize));
			clearDB();
			forceAutoUpdate = true;
		}

		// Prüfen ob Abschluß gewählt wurde
		boolean noDegreeSelected = false;
		if (StaticSessionData.sPreferences.getString(
				getString(R.string.Preference_Degree), "").equals("")) {
			selectDegree();
			forceAutoUpdate = false;
			noDegreeSelected = true;
		}

		ACTUAL_FILTER = getDefaultListFilter();

		Log.d(TAG, "create resolver");
		final ContentResolver resolver = getContentResolver();

		lv = (ListView) findViewById(R.id.gradesListView);
		checkSession();
		cursor = resolver.query(ExamsCol.CONTENT_URI, null, null, null, null);
		startManagingCursor(cursor);

		semMap = new HashMap<String, Integer>();

		final String[] from = new String[] { ExamsCol.EXAMNAME,
				ExamsCol.EXAMNR, ExamsCol.ATTEMPTS, ExamsCol.GRADE };
		final int[] to = new int[] { R.id.examName, R.id.examNr,
				R.id.examGrade, R.id.examAttempts, R.id.examGrade };
		mExamAdapter = new ExamDBAdapter(GradesList.this,
				R.layout.grade_row_item, cursor, from, to);
		lv.setAdapter(mExamAdapter);

		autoUpdate = StaticSessionData.sPreferences.getBoolean(
				getString(R.string.Preference_AutoUpdate), true);
		if (!noDegreeSelected
				&& (mExamAdapter.getCount() == 0 || forceAutoUpdate)) {
			updateGrades();
			if (forceAutoUpdate) {
				forceAutoUpdate = false;
			}
		} else if (autoUpdate) {
			refreshList();
			updateGrades();
		} else {
			refreshList();
		}

		this.mExamAdapter.getFilter().filter(getDefaultListFilter());

		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {

				// Log.d(TAG, "itemid: " + mExamAdapter.getItemId(position));
				long itemID = mExamAdapter.getItemId(position);
				String selection = BaseColumns._ID + " LIKE ?";

				Cursor cur = getContentResolver().query(ExamsCol.CONTENT_URI,
						null, selection,
						new String[] { String.valueOf(itemID) }, null);
				startManagingCursor(cur);
				final String out;
				if (cur.moveToFirst()) {
					out = cur.getString(
							cur.getColumnIndexOrThrow(ExamsCol.LINKID))
							.toString();
					// Log.d(TAG, "out: [" + out + "]");
					final String name = cur.getString(
							cur.getColumnIndexOrThrow(ExamsCol.EXAMNAME))
							.toString();
					final String nr = cur.getString(
							cur.getColumnIndexOrThrow(ExamsCol.EXAMNR))
							.toString();
					final String semester = cur.getString(
							cur.getColumnIndexOrThrow(ExamsCol.SEMESTER))
							.toString();
					final String grade = cur.getString(
							cur.getColumnIndexOrThrow(ExamsCol.GRADE))
							.toString();
					final String studiengang = cur.getString(
							cur.getColumnIndexOrThrow(ExamsCol.STUDIENGANG))
							.toString();
					if (!out.equals("0") && !grade.equals("0,0")) { // FIXME

						// Log.d(TAG, "show examInfo");
						showTitleProgress();
						showToast(String.format(
								getString(R.string.loadGradeDistrib), name));
						setRequestedOrientation(2);

						// startThread

						mExamInfoThread = new ExamInfoThread();
						Log.d(TAG, "name: " + name);
						Log.d(TAG, "nr: " + nr);
						Log.d(TAG, "sem: " + semester);
						Log.d(TAG, "linkID: " + out);
						Log.d(TAG, "studiengang: " + studiengang);
						mExamInfoThread.execute(new String[] { name, nr,
								semester, out, studiengang });
						// //
					} else {
						String gradeDistribError = String
								.format(getString(R.string.error_noGradeDistributionAvailableForX),
										name);
						showToast(gradeDistribError);
					}
				}

			}
		});

	}

	/**
	 * Prüfen ob Session noch gültig ist. Wenn nich, sprung zur Login Activity.
	 */
	private void checkSession() {
		if (!StaticSessionData.isCookieValid()) {
			// TODO relogin wenn cookie abgelaufen
			// doLogin();
			showToast("Cookie abgelaufen. Bitte neu anmelden.");
			Intent mainIntent = new Intent(this, HsDroidMain.class);
			// Alle aktivities darüber schließen..
			mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(mainIntent);
			finish();
		}
	}

	/**
	 * Abschlußauswahl Dialog. (Bachelor/Master).
	 */
	private void selectDegree() {
		AlertDialog.Builder builderSelectDegree = new AlertDialog.Builder(this);
		builderSelectDegree.setTitle(getString(R.string.text_degreeDesc));
		final int BACHELOR = 0;
		final int MASTER = 1;
		builderSelectDegree.setItems(R.array.degreeEntryArray,
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						Editor ed = sPreferences.edit();
						switch (which) {
						case BACHELOR:
							ed.putString(getString(R.string.Preference_Degree),
									"58");
							break;
						case MASTER:
							ed.putString(getString(R.string.Preference_Degree),
									"59");
							break;
						default:
							break;
						}
						ed.commit();
						updateGrades();
					}
				});
		builderSelectDegree.create().show();
	}

	private String getDefaultListOrder() {
		String prefOrder = sPreferences.getString(
				getString(R.string.Preference_DefaultSortOrder), "DESC");
		return prefOrder;
	}

	private String getDefaultListFilter() {
		return sPreferences.getString(
				getString(R.string.Preference_DefaultFilter), ACTUAL_FILTER);
	}

	/**
	 * ProgresDialog Handler
	 */
	final Handler mProgressHandle = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			Log.d("handler msg.what:", String.valueOf(msg.what));

			switch (msg.what) {

			case HANDLER_MSG_REFRESH:
				// ListView wieder neu laden

				refreshList();
				// Bildschirm Orientierung wieder dem User überlassen
				setRequestedOrientation(-1);
				hideTitleProgress();
				updateThread.cancel(true);
				break;
			case HANDLER_MSG_ERROR:
				showToast("Error, check logcat.");
				// Bildschirm Orientierung wieder dem User überlassen
				setRequestedOrientation(-1);
				hideTitleProgress();
				break;
			case HANDLER_MSG_INFO_READY:
				if (examinfoCursor == null) {
					Log.d(TAG, "cursor null");
				}

				new ExamInfoDialog(GradesList.this, msg.getData().getString(
						"Name"), msg.getData().getString("Nr"), msg.getData()
						.getString("Semester"), examinfoCursor);
				// dismissDialog(DIALOG_PROGRESS);
				hideTitleProgress();
				mExamInfoThread.cancel(true);

				// schließe progress und zeige infodialog
				break;

			default:
				Log.d(TAG, "default hide()");
				hideTitleProgress();
				break;
			}
		}
	};
	private UpdateThread updateThread;

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_PROGRESS:
			mProgressDialog = new ProgressDialog(this);
			// mProgressDialog.setMessage(this.getString(R.string.progress_connect));
			mProgressDialog.setIndeterminate(true);
			mProgressDialog.setCancelable(false);
			return mProgressDialog;

		default:
			return null;
		}
	}

	@Override
	protected void onRestoreInstanceState(Bundle outState) {
		super.onRestoreInstanceState(outState);

		// examsTest = (ArrayList<Exam>) outState.get("exams_list");
		// this.m_examAdapter.getFilter().filter(GradesList.ACTUAL_SORT);
		// FIXME nicht default , alte sortierung.!!

		// System.out.println("test onRestoreInstanceState");
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		// outState.putParcelableArrayList("exams_list", examsTest);
		// System.out.println("test onSaveInstanceState");

	}

	@Override
	protected void onPause() {
		super.onPause();
		Log.d(TAG, "pause... try to kill running threads");
		if (updateThread != null
				&& updateThread.getStatus() != AsyncTask.Status.FINISHED) {
			updateThread.cancel(true);
		}
		if (mExamInfoThread != null
				&& mExamInfoThread.getStatus() != AsyncTask.Status.FINISHED) {
			mExamInfoThread.cancel(true);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.d(TAG, "onResume");
		checkSession();
		refreshList();
		ACTUAL_FILTER = getDefaultListFilter();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		StaticSessionData.reloadSharedPrefs(this);
		if (hasFocus) {
			this.mExamAdapter.getFilter().filter(ACTUAL_FILTER);
		}
		refreshList();
	}

	/**
	 * Optionsmenü
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.grade_menu, menu);
		return true;
	}

	/**
	 * Optionsmenü Callback
	 */
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_about:
			new AboutDialog(this);
			return true;
		case R.id.view_menu_refresh:

			updateGrades();

			return true;
		case R.id.view_menu_preferences:
			Intent settingsActivity = new Intent(getBaseContext(),
					Preferences.class);
			startActivity(settingsActivity);
			// StaticSessionData.getSharedPrefs(this);
			ACTUAL_FILTER = getDefaultListFilter();
			return true;
		case R.id.view_submenu_examViewAll:
			if (item.isChecked())
				item.setChecked(false);
			else
				item.setChecked(true);

			ACTUAL_FILTER = FILTER_ALL;
			mExamAdapter.getFilter().filter(ACTUAL_FILTER);
			return true;
		case R.id.view_submenu_examViewOnlyLast:
			if (item.isChecked())
				item.setChecked(false);
			else
				item.setChecked(true);
			ACTUAL_FILTER = FILTER_ACTUAL;
			mExamAdapter.getFilter().filter(ACTUAL_FILTER);
			return true;
		case R.id.view_submenu_examViewOnlyLastFailed:
			if (item.isChecked())
				item.setChecked(false);
			else
				item.setChecked(true);

			ACTUAL_FILTER = FILTER_ACTUAL_FAILED;
			mExamAdapter.getFilter().filter(ACTUAL_FILTER);
			return true;
		case R.id.view_submenu_examViewOnlyLastPassed:
			if (item.isChecked())
				item.setChecked(false);
			else
				item.setChecked(true);

			ACTUAL_FILTER = FILTER_ACTUAL_PASSED;
			mExamAdapter.getFilter().filter(ACTUAL_FILTER);
			return true;
		case R.id.view_submenu_examViewAllFailed:
			if (item.isChecked())
				item.setChecked(false);
			else
				item.setChecked(true);

			ACTUAL_FILTER = FILTER_ALL_FAILED;
			mExamAdapter.getFilter().filter(ACTUAL_FILTER);
			return true;
		case R.id.view_submenu_examViewAllPassed:
			if (item.isChecked())
				item.setChecked(false);
			else
				item.setChecked(true);

			ACTUAL_FILTER = FILTER_ALL_PASSED;
			mExamAdapter.getFilter().filter(ACTUAL_FILTER);
			return true;
		default:
			Log.d("GradeView menu:", "default");
			return super.onOptionsItemSelected(item);
		}

	}

	/**
	 * Datenbank leeren
	 */
	private void clearDB() {
		AsyncTask<Void, Void, Void> clrDB = new AsyncTask<Void, Void, Void>() {

			@Override
			protected Void doInBackground(Void... params) {
				try {
					final ContentResolver resolver = getContentResolver();
					resolver.delete(ExamsCol.CONTENT_URI, null, null);

				} catch (Exception e) {
					e.printStackTrace();

				}
				return null;
			}
		};
		clrDB.execute();

	}

	/**
	 * Notenspiegel aktualisieren.
	 */
	private void updateGrades() {

		showTitleProgress();
		showToast(getString(R.string.info_updateGradesList));
		setRequestedOrientation(2);
		this.updateThread = new UpdateThread();
		this.updateThread.execute();

	}

	/**
	 * Liste neu Laden
	 */
	private void refreshList() {
		fillSemesterHashMap();
		this.lv.invalidateViews();
	}

	/**
	 * Hilfsmethode zum erzeugen von Dialogen
	 * 
	 * @param title
	 *            {@link String} Dialogtitel
	 * @param text
	 *            {@link String} Dialogtext
	 */
	private void createDialog(String title, String text) {
		AlertDialog ad = new AlertDialog.Builder(this)
				.setPositiveButton(this.getString(R.string.error_ok), null)
				.setTitle(title).setMessage(text).create();
		ad.show();
	}

	/**
	 * Gibt das aktuelle Semester zurück
	 * 
	 * @return ein {@link String} ("WiSe XX/XX" oder "SoSe XX")
	 */
	private String getActualExamSem() {
		String semString = "";
		Date dt = new Date();
		int year = dt.getYear() - 100;
		int month = dt.getMonth() + 1;
		// Log.d(TAG, "actualSemTest: mon:" + month + " yr:" + year);
		if (month > 9 || month < 3) { // zwischen okt und feb ist WS
			// wenn nicht januar oder februar ist, jahr+1
			if (month != 1 && month != 2) {
				year++;
			}
			semString = "WiSe " + (year - 1) + "/" + year;

		} else {// ansonsten SS
			semString = "SoSe " + year;
		}
		return semString;
	}

	/**
	 * 
	 * @param nExam
	 * @return
	 */
	private boolean isActualExam(String sem) {
		return sem.equals(getActualExamSem());
	}

	// ///////
	// Threads
	// ///////

	/**
	 * Noten Update Thread
	 * 
	 * @author Oliver Eichner
	 * 
	 */
	private class UpdateThread extends AsyncTask<Void, int[], Void> {

		@Override
		protected Void doInBackground(Void... params) {

			try {
				// ContentProvider öffnen
				final ContentResolver resolver = getContentResolver();
				// Cursor setzen
				final Cursor cursor = resolver.query(
						ExamsUpdateCol.CONTENT_URI, null, null, null, null);
				startManagingCursor(cursor);

				cursor.close();

			} catch (Exception e) {
				mProgressHandle.sendMessage(mProgressHandle
						.obtainMessage(HANDLER_MSG_ERROR));
				// hideTitleProgress();
				// createDialog(GradesList.this.getString(R.string.error),
				// e.getMessage());
				e.printStackTrace();
			}

			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			// Dem Handler bescheid sagen, dass die Daten nun
			// verfügbar sind
			mProgressHandle.sendMessage(mProgressHandle
					.obtainMessage(HANDLER_MSG_REFRESH));
			super.onPostExecute(result);
		}

	}

	/**
	 * Thread für Notenverteilung
	 * 
	 * @author Oliver Eichner
	 * 
	 */
	public class ExamInfoThread extends AsyncTask<String[], Integer, Bundle> {

		@Override
		protected Bundle doInBackground(String[]... params) {
			try {
				// System.out.println("do..examinfothread");
				// ContentProvider öffnen
				final ContentResolver resolver = getContentResolver();
				// Cursor setzen

				// FIXME check ob 4 strings übergeben wurden..

				// String out = params[0][3];
				examinfoCursor = resolver
						.query(ExamInfos.CONTENT_URI, null, null, new String[] {
								params[0][3], params[0][4] }, null);
				startManagingCursor(examinfoCursor);
				examinfoCursor.moveToFirst();

				if (isCancelled()) {
					stopManagingCursor(examinfoCursor);
					return null;
				}
				// Dem Handler bescheid sagen, dass die Daten nun verfügbar sind
				Message oMessage = mProgressHandle.obtainMessage();
				Bundle oBundle = new Bundle();

				// FIXME unnötiges rumgeschupse von daten.. name etc. über
				// cursor holen..
				oBundle.putString("Name", params[0][0]);
				oBundle.putString("Nr", params[0][1]);
				oBundle.putString("Semester", params[0][2]);
				System.out.println("params");
				for (String[] strings : params) {
					System.out.println("param: " + strings);
				}
				oMessage.setData(oBundle);
				oMessage.what = HANDLER_MSG_INFO_READY;
				mProgressHandle.sendMessage(oMessage);

			} catch (Exception e) {
				dismissDialog(DIALOG_PROGRESS);
				createDialog(GradesList.this.getString(R.string.error),
						e.getMessage());
				e.printStackTrace();
			} finally {
				stopManagingCursor(examinfoCursor);
			}
			return null;
		}
	}

	private HashMap<String, Integer> semMap = null;

	public void fillSemesterHashMap() {
		ContentResolver resolver = getContentResolver();
		String sortOrder = StaticSessionData.sPreferences.getString(
				getString(R.string.Preference_DefaultSortOrder), "DESC");
		boolean incrementCounter = false;
		if (sortOrder.equals("ASC")) {
			sortOrder = "DESC";
		} else {
			sortOrder = "ASC";
			incrementCounter = true;
		}
		Cursor cursor = resolver.query(ExamsCol.CONTENT_URI, null, null, null,
				"_id " + sortOrder);
		cursor.moveToFirst();
		int count = 0;

		semMap = new HashMap<String, Integer>();

		while (!cursor.isAfterLast()) {
			if (cursor.isFirst()) {
				int dbIdOffset = cursor.getInt(cursor
						.getColumnIndex(BaseColumns._ID));
				// Log.d(TAG, "db offset: " + dbIdOffset);
				count = dbIdOffset;
			}
			semMap.put(
					cursor.getString(cursor.getColumnIndex(ExamsCol.SEMESTER)),
					count);
			cursor.moveToNext();
			if (incrementCounter) {
				count++;
			} else {
				count--;
			}
		}
		cursor.close();
	}

	/**
	 * {@link ListAdapter} für die Datenbank mit den Prüfungsleistungen
	 * 
	 * @author Oliver Eichner
	 * 
	 */
	public class ExamDBAdapter extends SimpleCursorAdapter {

		// private Context context;
		private int layout;

		public ExamDBAdapter(Context context, int layout, Cursor cursor,
				String[] from, int[] to) {
			super(context, layout, cursor, from, to);
			// this.context = context;
			this.layout = layout;
			Log.d(TAG, "Create ExamAdapter");
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
			// Log.d(TAG, "exAdapter newView");s
			final LayoutInflater inflater = LayoutInflater.from(context);
			View v = inflater.inflate(layout, viewGroup, false);

			return v;
		}

		@Override
		public void bindView(View v, Context context, Cursor c) {
			// Log.d(TAG, "exAdapter bindView");
			int nameCol = c.getColumnIndex(ExamsCol.EXAMNAME);
			String name = c.getString(nameCol);
			int rnCol = c.getColumnIndex(ExamsCol.EXAMNR);
			String nr = c.getString(rnCol);
			int attCol = c.getColumnIndex(ExamsCol.ATTEMPTS);
			int att = c.getInt(attCol);
			int gradeCol = c.getColumnIndex(ExamsCol.GRADE);
			String grade = c.getString(gradeCol);
			int semCol = c.getColumnIndex(ExamsCol.SEMESTER);
			String sem = c.getString(semCol);
			int passedCol = c.getColumnIndex(ExamsCol.PASSED);
			int passed = c.getInt(passedCol);

			TextView exName = (TextView) v.findViewById(R.id.examName);
			TextView exNr = (TextView) v.findViewById(R.id.examNr);
			TextView exAtt = (TextView) v.findViewById(R.id.examAttempts);
			TextView exGrade = (TextView) v.findViewById(R.id.examGrade);
			TextView exSemester = (TextView) v.findViewById(R.id.examSemester);

			if (exName != null) {
				exName.setText(name);
				if (isActualExam(sem)
						&& StaticSessionData.sPreferences.getBoolean(
								"highlightActualExamsPref", false)) {
					exName.setShadowLayer(3, 0, 0, Color.GREEN);
				} else {
					exName.setShadowLayer(0, 0, 0, 0);
				}

				int a, b;
				if (semMap.size() > 0) {
					a = c.getInt(c.getColumnIndex(BaseColumns._ID));
					b = semMap.get(sem);
					// Log.d(TAG, "a:b - " + a + ":" + b);
					TextView separator = (TextView) v
							.findViewById(R.id.examSeparator);
					if (a == b
							&& StaticSessionData.sPreferences.getBoolean(
									"prefUseSeparator", true)) {

						separator.setText(sem);
						separator.setVisibility(TextView.VISIBLE);
					} else {
						separator.setVisibility(TextView.GONE);
					}
				}

			}
			if (exNr != null) {
				exNr.setText(nr);
			}
			if (exSemester != null) {
				exSemester.setText(getApplicationContext().getString(
						R.string.grades_view_semester)
						+ sem);
			}
			if (exAtt != null && att != 0) {
				exAtt.setText(getApplicationContext().getString(
						R.string.grades_view_attempt)
						+ att);
			}
			if (exGrade != null) {
				if (passed == 0) { // wenn nicht bestanden
					// FIXME wenn möglich.. farben gedöns is ziemlich tricky
					// wegen "recycler" von ListView
					if (att > 1) {
						exGrade.setTextColor(Color.BLACK);
						exGrade.setBackgroundColor(Color.RED);
						exGrade.setCompoundDrawablePadding(2);

					} else {
						exGrade.setTextColor(Color.RED);
						exGrade.setBackgroundColor(Color.TRANSPARENT);
					}
				} else { // wenn bestanden
					exGrade.setTextColor(Color.rgb(0x87, 0xeb, 0x0c));
					// exGrade.setTextColor(Color.GREEN);
					exGrade.setBackgroundColor(Color.TRANSPARENT);
				}

				if (grade.length() != 0) {
					// Log.d(TAG, "grade[" + grade + "]");
					exGrade.setText(grade);
				} else {
					if (passed == 0) { // Wenn nicht bestanden
						// Log.d(TAG, "grade NB [" + grade + "]");
						exGrade.setText("NB");
					} else { // Wenn bestanden
						// Log.d(TAG, "grade BE [" + grade + "]");
						exGrade.setText("BE");

					}
				}
			}

		}

		public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
			// Log.d(TAG, "runQueryOnBackgroundThread");
			// Log.d(TAG, "rqbg constraint: " + constraint);

			String sortOrder = BaseColumns._ID + " " + getDefaultListOrder();

			if (getFilterQueryProvider() != null) {
				return getFilterQueryProvider().runQuery(constraint);
			}
			// Log.d(TAG, "no FilterQueryProvider");
			if (constraint.equals(FILTER_ALL)) {
				return getContentResolver().query(ExamsCol.CONTENT_URI, null,
						null, null, sortOrder);
			} else if (constraint.equals(FILTER_ALL_FAILED)) {
				// System.out.println("sort all failed");
				StringBuilder buffer = null;
				String[] args = null;

				buffer = new StringBuilder();
				buffer.append("UPPER(");
				buffer.append(ExamsCol.PASSED);
				buffer.append(") LIKE ?");
				args = new String[] { "0" };
				// Log.d(TAG, "buffer: " + buffer.toString());
				// Log.d(TAG, "args: " + args[0]);
				return getContentResolver().query(ExamsCol.CONTENT_URI, null,
						buffer == null ? null : buffer.toString(), args,
						sortOrder);
			} else if (constraint.equals(FILTER_ALL_PASSED)) {
				// System.out.println("sort all failed");
				StringBuilder buffer = null;
				String[] args = null;

				buffer = new StringBuilder();
				buffer.append("UPPER(");
				buffer.append(ExamsCol.PASSED);
				buffer.append(") LIKE ?");
				args = new String[] { "1" };
				// Log.d(TAG, "buffer: " + buffer.toString());
				// Log.d(TAG, "args: " + args[0]);
				return getContentResolver().query(ExamsCol.CONTENT_URI, null,
						buffer == null ? null : buffer.toString(), args,
						sortOrder);
			} else if (constraint.equals(FILTER_ACTUAL)) {

				StringBuilder buffer = null;
				String[] args = null;

				buffer = new StringBuilder();
				buffer.append("UPPER(");
				buffer.append(ExamsCol.SEMESTER);
				buffer.append(") LIKE ?");

				args = new String[] { getActualExamSem() };
				// Log.d(TAG, "buffer: " + buffer.toString());
				// Log.d(TAG, "args: " + args[0]);
				return getContentResolver().query(ExamsCol.CONTENT_URI, null,
						buffer == null ? null : buffer.toString(), args,
						sortOrder);
			} else if (constraint.equals(FILTER_ACTUAL_FAILED)) {

				StringBuilder buffer = null;
				String[] args = null;

				buffer = new StringBuilder();
				buffer.append("UPPER(");
				buffer.append(ExamsCol.SEMESTER);
				buffer.append(") LIKE ? AND UPPER(");
				buffer.append(ExamsCol.PASSED);
				buffer.append(") LIKE ?");
				args = new String[] { getActualExamSem(), "0" };
				// Log.d(TAG, "buffer: " + buffer.toString());
				// Log.d(TAG, "args: " + args[0]);

				return getContentResolver().query(ExamsCol.CONTENT_URI, null,
						buffer == null ? null : buffer.toString(), args,
						sortOrder);
			} else if (constraint.equals(FILTER_ACTUAL_PASSED)) {

				StringBuilder buffer = null;
				String[] args = null;

				buffer = new StringBuilder();
				buffer.append("UPPER(");
				buffer.append(ExamsCol.SEMESTER);
				buffer.append(") LIKE ? AND UPPER(");
				buffer.append(ExamsCol.PASSED);
				buffer.append(") LIKE ?");
				args = new String[] { getActualExamSem(), "1" };
				// Log.d(TAG, "buffer: " + buffer.toString());
				// Log.d(TAG, "args: " + args[0]);

				return getContentResolver().query(ExamsCol.CONTENT_URI, null,
						buffer == null ? null : buffer.toString(), args,
						sortOrder);
			} else {
				return null;
			}
		}
	}

}
