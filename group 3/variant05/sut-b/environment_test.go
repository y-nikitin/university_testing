//go:build integration

package rental

import (
	"context"
	"testing"
	"time"
)

func TestDatabaseConnection(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	db, err := OpenDatabase(ctx)
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
}
