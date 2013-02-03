/*
 * Copyright (C) 2012 Jamie Nicol <jamie@thenicols.net>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.jamienicol.nextepisode;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import org.jamienicol.nextepisode.db.EpisodesTable;
import org.jamienicol.nextepisode.db.ShowsProvider;

public class EpisodeActivity extends Activity
	implements LoaderManager.LoaderCallbacks<Cursor>
{
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.episode_activity);

		getActionBar().setDisplayHomeAsUpEnabled(true);

		Intent intent = getIntent();
		int episodeId = intent.getIntExtra("episodeId", -1);
		if (episodeId == -1) {
			throw new IllegalArgumentException("must provide valid episodeId");
		}

		Bundle loaderArgs = new Bundle();
		loaderArgs.putInt("episodeId", episodeId);
		getLoaderManager().initLoader(0, loaderArgs, this);
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		int episodeId = args.getInt("episodeId");
		Uri uri = Uri.withAppendedPath(ShowsProvider.CONTENT_URI_EPISODES,
		                               new Integer(episodeId).toString());
		String[] projection = {
			EpisodesTable.COLUMN_NAME
		};
		return new CursorLoader(this,
		                        uri,
		                        projection,
		                        null,
		                        null,
		                        null);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		if (data.getCount() >= 1) {
			int columnIndex =
				data.getColumnIndexOrThrow(EpisodesTable.COLUMN_NAME);

			data.moveToFirst();
			setTitle(data.getString(columnIndex));
		} else {
			setTitle("");
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		setTitle("");
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			finish();
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}
}