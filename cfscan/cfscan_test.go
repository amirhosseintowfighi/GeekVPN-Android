package cfscan

import "testing"

func TestVersionIsReportedForTheAboutScreen(t *testing.T) {
	if Version() == "" {
		t.Fatal("Version() must not be empty")
	}
}
