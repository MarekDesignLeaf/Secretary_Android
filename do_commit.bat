@echo off
git add -A
git commit -m "fix: remove legacy firstLoginUsers auth flow, add wipe_all_data, reset_user_password, clean-install cache purge"
git push origin clean-first-install-api:main --force-with-lease
echo DONE
