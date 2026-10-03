-- V8: Rename wishlist status to WATCHLIST and update constraints
-- Update existing rows from lowercase 'watchlist' to uppercase 'WATCHLIST'
UPDATE library_entries SET status = 'WATCHLIST' WHERE status = 'watchlist';

-- Drop and recreate the check constraint
ALTER TABLE library_entries DROP CONSTRAINT library_entries_status_check;
ALTER TABLE library_entries ADD CONSTRAINT library_entries_status_check CHECK (status IN ('WATCHLIST', 'IN_PROGRESS', 'COMPLETED', 'DROPPED', 'ON_HOLD'));

