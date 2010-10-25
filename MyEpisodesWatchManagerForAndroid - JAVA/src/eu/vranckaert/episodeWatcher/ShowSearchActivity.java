package eu.vranckaert.episodeWatcher;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import eu.vranckaert.episodeWatcher.domain.Show;
import eu.vranckaert.episodeWatcher.domain.User;
import eu.vranckaert.episodeWatcher.exception.InternetConnectivityException;
import eu.vranckaert.episodeWatcher.exception.LoginFailedException;
import eu.vranckaert.episodeWatcher.exception.ShowAddFailedException;
import eu.vranckaert.episodeWatcher.exception.UnsupportedHttpPostEncodingException;
import eu.vranckaert.episodeWatcher.preferences.Preferences;
import eu.vranckaert.episodeWatcher.preferences.PreferencesKeys;
import eu.vranckaert.episodeWatcher.service.MyEpisodesService;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dirk Vranckaert
 *         Date: 18-sep-2010
 *         Time: 17:20:41
 */
public class ShowSearchActivity extends ListActivity {
    private static final String LOG_TAG = "SHOW_SEARCH_AVTIVITY";

    private static final int DIALOG_LOADING = 0;
    private static final int DIALOG_EXCEPTION = 1;
    private static final int DIALOG_FINISHED = 2;
    private static final int DIALOG_ADD_SHOW = 3;

    private MyEpisodesService service;
    private User user;
    private ShowAdapter showAdapter;
    private List<Show> shows = new ArrayList<Show>(0);

    private Integer exceptionMessageResId = null;
    private Integer showListPosition = null;

    private boolean showsAdded = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        init(savedInstanceState);

        ImageButton searchButton = (ImageButton) findViewById(R.id.searchButton);
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CharSequence query = ((EditText) findViewById(R.id.searchQuery)).getText();
                if(query.length() > 0) {
                    ShowSearchActivity.this.searchShows(query.toString());
                } else {
                    //TODO show message: enter a show name to search for!! Use a toast-message for this purpose!
                }
            }
        });
    }

    private void init(Bundle savedInstanceState) {
    	setTheme(Preferences.getPreferenceInt(this, PreferencesKeys.THEME_KEY) == 0 ? android.R.style.Theme_Light : android.R.style.Theme);
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.showsearch);

        service = new MyEpisodesService();
        user = new User(
            Preferences.getPreference(this, User.USERNAME),
            Preferences.getPreference(this, User.PASSWORD)
        );

        initializeShowList();
    }

    private void initializeShowList() {
        showAdapter = new ShowAdapter(this, R.layout.searchresultsshowsrow, shows);
        setListAdapter(showAdapter);
    }

    private void updateShowList() {
        showAdapter.clear();
        for(Show show : shows) {
            showAdapter.add(show);
        }
        showAdapter.notifyDataSetChanged();
    }

    private void updateNumberOfResults() {
        TextView numberOfResults = (TextView) findViewById(R.id.showNameSearchNumberOfResults);

        if(shows.size() > 0) {
            String text = shows.size() + " ";

            if(shows.size() == 1) {
                text += getText(R.string.showSearchOneFound);
            } else {
                text += getText(R.string.showSearchMoreFound);
            }
            numberOfResults.setText(text);
            numberOfResults.setVisibility(TextView.VISIBLE);
        } else {
            numberOfResults.setVisibility(TextView.GONE);
        }
    }

    @Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog = null;
		switch (id) {
			case DIALOG_LOADING: {
				ProgressDialog progressDialog = new ProgressDialog(this);
				progressDialog.setMessage(this.getString(R.string.progressLoadingTitle));
                progressDialog.setCancelable(false);
				dialog = progressDialog;
				break;
            }
            case DIALOG_EXCEPTION: {
				if (exceptionMessageResId == null) {
					exceptionMessageResId = R.string.defaultExceptionMessage;
				}
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(R.string.exceptionDialogTitle)
					   .setMessage(exceptionMessageResId)
					   .setCancelable(false)
					   .setPositiveButton(R.string.dialogOK, new DialogInterface.OnClickListener() {
				           public void onClick(DialogInterface dialog, int id) {
				                dialog.cancel();
				           }
				       });
				dialog = builder.create();
                exceptionMessageResId = null;
                break;
            }
            case DIALOG_FINISHED: {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.showSearchFinished)
                       .setCancelable(false)
                       .setPositiveButton(R.string.done, new DialogInterface.OnClickListener() {
				           public void onClick(DialogInterface dialog, int id) {
				               dialog.cancel();
                               finish();
				           }
				       })
                       .setNegativeButton(R.string.search, new DialogInterface.OnClickListener() {
				           public void onClick(DialogInterface dialog, int id) {
				               dialog.cancel();
				           }
				       });
                dialog = builder.create();
                break;
            }
            case DIALOG_ADD_SHOW: {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(shows.get(showListPosition).getShowName())
                       .setMessage(R.string.showSearchAddShow)
                       .setCancelable(false)
                       .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
				           public void onClick(DialogInterface dialog, int id) {
				               dialog.cancel();
                               addShowByListPosition(showListPosition);
				           }
				       })
                       .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
				           public void onClick(DialogInterface dialog, int id) {
				               dialog.cancel();
				           }
				       });
                showListPosition = null;
                dialog = builder.create();
                break;
            }
        }
        return dialog;
    }

    private void searchShows(final String query) {
        AsyncTask<Void, Void, Void> asyncTask = new AsyncTask() {
            @Override
            protected void onPreExecute() {
                showDialog(DIALOG_LOADING);
            }

            @Override
            protected Object doInBackground(Object... objects) {
                doSearch(query);
                return 100L;
            }

            @Override
            protected void onPostExecute(Object o) {
                if(exceptionMessageResId != null && !exceptionMessageResId.equals("")) {
                    dismissDialog(DIALOG_LOADING);
                    showDialog(DIALOG_EXCEPTION);
                } else {
                    updateNumberOfResults();
                    updateShowList();
                    dismissDialog(DIALOG_LOADING);
                }
            }
        };
        asyncTask.execute();
    }

    private void doSearch(String query) {
        try {
            shows = service.searchShows(query.toString(), user);
            Log.d(LOG_TAG, shows.size() + " show(s) found!!!");
            exceptionMessageResId = null;
        } catch (UnsupportedHttpPostEncodingException e) {
            String message = "Network issues";
			Log.e(LOG_TAG, message, e);
			exceptionMessageResId = R.string.networkIssues;
        } catch (InternetConnectivityException e) {
            String message = "Could not connect to host";
			Log.e(LOG_TAG, message, e);
			exceptionMessageResId = R.string.internetConnectionFailureReload;
        } catch (LoginFailedException e) {
            String message = "Login failure";
			Log.e(LOG_TAG, message, e);
			exceptionMessageResId = R.string.networkIssues;
        }
    }

    private class ShowAdapter extends ArrayAdapter<Show> {
        private List<Show> shows;

        public ShowAdapter(Context context, int textViewResourceId, List<Show> el) {
            super(context, textViewResourceId, el);
            this.shows = el;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final int i = position;
            View row = convertView;
            if (row==null) {
                LayoutInflater inflater = getLayoutInflater();
                row = inflater.inflate(R.layout.searchresultsshowsrow, parent, false);
            }

            TextView topText = (TextView) row.findViewById(R.id.showNameSearchResult);

            Show show = shows.get(position);
            topText.setText(show.getShowName());
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    //TODO show popup requesting if the user really wants to add the show or not!
                    showListPosition = i;
                    showDialog(DIALOG_ADD_SHOW);
                }
            });

            return row;
        }
    }

    private void addShowByListPosition(final int position) {
        AsyncTask<Void, Void, Void> asyncTask = new AsyncTask() {
            @Override
            protected void onPreExecute() {
                showDialog(DIALOG_LOADING);
            }

            @Override
            protected Object doInBackground(Object... objects) {
                Show show = shows.get(position);
                addShow(show);
                return 100L;
            }

            @Override
            protected void onPostExecute(Object o) {
                dismissDialog(DIALOG_LOADING);
                if(exceptionMessageResId != null && !exceptionMessageResId.equals("")) {
                    showDialog(DIALOG_EXCEPTION);
                } else {
                    showDialog(DIALOG_FINISHED);
                }
            }
        };
        asyncTask.execute();
    }

    private void addShow(Show show) {
        try {
            Log.d(LOG_TAG, "Adding show with id " + show.getMyEpisodeID() + " to the account of user " + user.getUsername());
            service.addShow(show.getMyEpisodeID(), user);
            showsAdded = true;
        } catch (InternetConnectivityException e) {
            String message = "Could not connect to host";
			Log.e(LOG_TAG, message, e);
			exceptionMessageResId = R.string.internetConnectionFailureReload;
        } catch (LoginFailedException e) {
            String message = "Login failure";
			Log.e(LOG_TAG, message, e);
			exceptionMessageResId = R.string.networkIssues;
        } catch (UnsupportedHttpPostEncodingException e) {
            String message = "Network issues";
			Log.e(LOG_TAG, message, e);
			exceptionMessageResId = R.string.networkIssues;
        } catch (ShowAddFailedException e) {
            String message = "Could not add show";
			Log.e(LOG_TAG, message, e);
			exceptionMessageResId = R.string.searchShowUnabletoAdd;
        }
    }

    @Override
    public void finish() {
        if(showsAdded) {
            setResult(RESULT_OK);
        } else {
            setResult(RESULT_CANCELED);
        }
        super.finish();
    }
}