package me.magnum.melonds.model;

public class RendererConfiguration {
	private VideoFiltering videoFiltering;

	public RendererConfiguration(VideoFiltering videoFiltering) {
		this.videoFiltering = videoFiltering;
	}

	public VideoFiltering getVideoFiltering() {
		return videoFiltering;
	}
}
