/*
 * Copyright (C) 2012-2014 Jamie Nicol <jamie@thenicols.net>
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

package com.redcoracle.episodes.tvdb;

import java.util.Date;
import java.util.List;

public class Show {
	private int id;
	private int tvdbId;
	private int tmdbId;
	private String imdbId;
	private String name;
	private String language;
	private String overview;
	private Date firstAired;
	private String bannerPath;
	private String fanartPath;
	private String posterPath;
	private List<Episode> episodes;

	public Show() {
		name = "";
		language = "";
		overview = "";
		firstAired = null;
		bannerPath = "";
		fanartPath = "";
		posterPath = "";
		episodes = null;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getTvdbId() {
		return tvdbId;
	}

	public void setTvdbId(int tvdbId) {
		this.tvdbId = tvdbId;
	}

	public int getTmdbId() {
		return this.tmdbId;
	}

	public void setTmdbId(int tmdbId) {
		this.tmdbId = tmdbId;
	}

	public String getImdbId() {
		return this.imdbId;
	}

	public void setImdbId(String imdbId) {
		this.imdbId = imdbId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getLanguage() {
		return language;
	}

	void setLanguage(String language) {
		this.language = language;
	}

	public String getOverview() {
		return overview;
	}

	public void setOverview(String overview) {
		this.overview = overview;
	}

	public Date getFirstAired() {
		return firstAired;
	}

	void setFirstAired(Date firstAired) {
		this.firstAired = firstAired;
	}

	public String getBannerPath() {
		return bannerPath;
	}

	void setBannerPath(String bannerPath) {
		this.bannerPath = bannerPath;
	}

	public String getFanartPath() {
		return fanartPath;
	}

	void setFanartPath(String fanartPath) {
		this.fanartPath = fanartPath;
	}

	public String getPosterPath() {
		return posterPath;
	}

	public void setPosterPath(String posterPath) {
		this.posterPath = posterPath;
	}

	public List<Episode> getEpisodes() {
		return episodes;
	}

	public void setEpisodes(List<Episode> episodes) {
		this.episodes = episodes;
	}
}
