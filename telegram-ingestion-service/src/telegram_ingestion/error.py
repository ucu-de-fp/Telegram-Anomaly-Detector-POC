class GroupMappingNotFoundError(Exception):
    def __init__(self, telegram_group_id: str):
        super().__init__(f"Group mapping not found for telegram group with id={telegram_group_id}")
        self.telegram_group_id = telegram_group_id